package ud.binmonkey.prog3_proyecto_server.neo4j;

import org.neo4j.driver.v1.*;
import ud.binmonkey.prog3_proyecto_server.neo4j.omdb.MediaType;
import ud.binmonkey.prog3_proyecto_server.neo4j.omdb.Omdb;
import ud.binmonkey.prog3_proyecto_server.neo4j.omdb.OmdbMovie;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.FileHandler;
import java.util.logging.Level;

import static org.neo4j.driver.v1.Values.parameters;

public class Neo4j {

    private String username;
    private String password;
    private Driver driver;
    private Session session;

    /* Logger from Neo4j*/
    private static final boolean ADD_TO_FIC_LOG = false; /* set false to overwrite */
    private static java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Neo4j.class.getName());

    static {
        try {
            logger.addHandler(new FileHandler(
                    "logs/" + Neo4j.class.getName() + ".log.xml", ADD_TO_FIC_LOG));
        } catch (SecurityException | IOException e) {
            logger.log(Level.SEVERE, "Error in log file creation");
        }
    }

    /**
     * Constructor for the class Neoj
     */
    public Neo4j() {
        readConfig();
        startSession();
        clearDB();
    }

    public static void main(String[] args) {
        Neo4j neo4j = new Neo4j();

        neo4j.addTitle("tt0117951");
        neo4j.addTitle("tt0289043");
        neo4j.addTitle("tt0470752");
        neo4j.addTitle("tt0137523");

        neo4j.addTitle("tt0068646");
        neo4j.addTitle("tt0071562");
        neo4j.addTitle("tt0099674");

        neo4j.addTitle("tt0120737");
        neo4j.addTitle("tt0167261");
        neo4j.addTitle("tt0167260");

        neo4j.addTitle("tt0120915");
        neo4j.addTitle("tt0121765");
        neo4j.addTitle("tt2488496");
        neo4j.addTitle("tt0076759");
        neo4j.addTitle("tt0080684");
        neo4j.addTitle("tt0086190");
        neo4j.addTitle("tt0121766");

        neo4j.addTitle("tt0301357");

        neo4j.closeSession();

    }

    private void readConfig() {
        /* TODO store this on a .xml */
        username = "test";
        password = "test";
    }

    public void startSession() {
        driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic(username, password));
        session = driver.session();
    }

    public void closeSession() {
        session.close();
        driver.close();
    }

    /**
     * Adds an IMDB title to the DB
     *
     * @param id - IMDB id of the title
     */
    public void addTitle(String id) {
        /* TODO Implement Series and Episodes */
        if (!checkNode(id, "Movie")) {
            if (MediaType.movie.equalsName((String) Omdb.getTitle(id).get("Type"))) {

                OmdbMovie movie = new OmdbMovie(id);

                /* Add info of Movie */
                session.run(
                        "CREATE (a:Movie {title: {title}, name: {name}, year: {year}, released: {released}, dvd: {dvd}, plot: {plot}, rated: {rated}, awards: {awards}, boxOffice: {boxOffice}, metascore: {metascore}, imdbRating: {imdbRating}, imdbVotes: {imdbVotes}, runtime: {runtime}, website: {website}, poster: {poster}})",
                        (Value) movie.toParameters());

                logger.log(Level.INFO, "Added Movie: " + movie.getImdbID());

                addNodeList(movie.getLanguage(), "Language", id, "SPOKEN_LANGUAGE");
                addNodeList(movie.getGenre(), "Genre", id, "GENRE");
                addNodeList(movie.getWriter(), "Person", id, "WROTE");
                addNodeList(movie.getDirector(), "Person", id, "DIRECTED");
                addNodeList(movie.getActors(), "Person", id, "ACTED_IN");
                addNodeList(movie.getProducers(), "Producer", id, "PRODUCED");


                /* ScoreOutles TODO standarize scores */
                for (Object outlet : movie.getRatings().keySet()) {
                    if (!checkNode((String) outlet, "ScoreOutlet")) {
                        session.run("CREATE (a:ScoreOutlet {name: {name}})",
                                parameters("name", outlet));

                        logger.log(Level.INFO, "Added ScoreOutlet: " + outlet);
                    }

                    String score = (String) movie.getRatings().get(outlet);

                    session.run("MATCH (a:ScoreOutlet { name: {name}}), (b:Movie { name: {id}}) " +
                                    "CREATE (a)-[:SCORED {score: {score}}]->(b)"
                            , parameters("name", outlet, "score", score, "id", id));

                    logger.log(Level.INFO, "Added SCORED: " + outlet + " -(" + score + ")-> " + id);
                }
            }
        } else {
            logger.log(Level.WARNING, id + " already exists");
        }
    }

    /**
     * Takes a ArrayList of values and turns them into Nodes
     *
     * @param list          - List of values to turn into Nodes
     * @param node_type     - Type of the nodes to create
     * @param title         - Title the relation is assigned to
     * @param relation_type - Type of the relation between the node and the title
     */
    private void addNodeList(ArrayList list, String node_type, String title, String relation_type) {
        for (Object o : list) {
            String node = o.toString();
            if (!checkNode(node, node_type)) {

                session.run(
                        "CREATE(p:" + node_type + " {name: {name}})",
                        parameters("name", node));

                logger.log(Level.INFO, "Added " + node_type + ": " + node);
            } else {
                logger.log(Level.WARNING, node + " already exists");
            }

            session.run("MATCH (a:" + node_type + " { name: {name}}), (b:Movie { name: {title}}) " +
                    "CREATE (a)-[:" + relation_type + "]->(b)", parameters("name", node, "title", title));

            logger.log(Level.INFO, "Added " + relation_type + ": " + node + " -> " + title);
        }
    }

    /**
     * Check if a Node exists in the DB
     *
     * @param id - Identifier of the Node
     * @return true if the Node exists
     */
    private boolean checkNode(String id, String type) {

        boolean existance = false;
        StatementResult result = session.run("MATCH (a:" + type + ") RETURN a.name");

        while (result.hasNext()) {
            Record record = result.next();

            if (record.get("a.name").asString().equals(id)) {
                existance = true;
            }
        }
        return existance;
    }

    public void clearDB() {
        session.run("MATCH (n) DETACH DELETE n;");
        logger.log(Level.INFO, "Cleared DB");
    }
}