package ud.binmonkey.prog3_proyecto_server.ftp;

import org.apache.commons.io.FileUtils;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.impl.DefaultFtpServer;
import org.apache.ftpserver.impl.FtpServerContext;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import ud.binmonkey.prog3_proyecto_server.common.DocumentReader;
import ud.binmonkey.prog3_proyecto_server.common.exceptions.*;
import ud.binmonkey.prog3_proyecto_server.common.security.UserAuthentication;
import ud.binmonkey.prog3_proyecto_server.common.time.DateUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FTPServer extends DefaultFtpServer{

    /* TODO: allow no more than 1 instances of FTPServer */
    private static final Logger LOG = Logger.getLogger(FtpServer.class.getName());
    private static final String ftpd = DocumentReader.getAttr(DocumentReader.getDoc("conf/properties.xml"),
            "network", "ftp-server", "ftpd").getTextContent();
    private static final String userFile = DocumentReader.getAttr(DocumentReader.getDoc("conf/properties.xml"),
            "network", "ftp-server", "user-file").getTextContent();
    private static final String ftpLetFile = DocumentReader.getAttr(DocumentReader.getDoc("conf/properties.xml"),
            "network", "ftp-server", "ftplet-file").getTextContent();

    static {
        try {
            LOG.addHandler(new FileHandler(
                    "logs/" + FTPServer.class.getName() + "." +
                            DateUtils.currentFormattedDate() + ".log.xml", true));
        } catch (SecurityException | IOException e) {
            LOG.log(Level.SEVERE, "Unable to create log file.");
        }
    }

    /**
     * Default constructor
     * @param serverContext FTP context
     */
    public FTPServer(FtpServerContext serverContext) {
        super(serverContext);
    }

    /**
     * Run before @start()
     * Creates admin and common users
     */
    public static void init() {
        try {
            createAdmin();
            createCommon();

            /* create common directory */
            File commonDir = new File(ftpd + "/common/data/images/");
            if (!commonDir.exists()) {
                commonDir.mkdirs();
            }
            ud.binmonkey.prog3_proyecto_server.common.filesystem.FileUtils.mkPath(ftpd + "/common/data/images");
        } catch (FtpException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if FTP user exists
     * @param userName username to be checked
     * @param userManager userManager to search for user
     * @return true if user exists, false if not
     */
    @SuppressWarnings("WeakerAccess") /* might be useful from outside */
    static boolean userExists(String userName, UserManager userManager) throws FtpException {
        return userManager.getUserByName(userName) != null;
    }

    /**
     * XXX: ONLY USE THIS METHOD FOR USER MANAGEMENT
     * @param userName username of new user
     * @param password password of new user
     */
    public static void createUser(String userName, String password) throws FtpException,
            NewUserExistsException, AdminEditException, InvalidNameException {

        /* lowercase usernames */
        userName = userName.toLowerCase();
        UserAuthentication.checkAdmin(userName);
        UserAuthentication.isValidName(userName);

        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        userManagerFactory.setFile(new File(userFile));

        UserManager userManager = userManagerFactory.createUserManager();

        if (!userExists(userName, userManager)) {
            /* create basic user */
            BaseUser user = new BaseUser();
            user.setName(userName);
            user.setPassword(password);
            user.setHomeDirectory(ftpd + userName);

            /* give permissions */
            List<Authority> authorities = new ArrayList<>();
            authorities.add(new WritePermission());
            user.setAuthorities(authorities);

            /* save user */
            userManager.save(user);
            LOG.log(Level.INFO, "New user `" + userName + "` created.");
        } else {
            throw new NewUserExistsException(userName);
        }
    }

    /**
     * Create admin user
     */
    public static void createAdmin() throws FtpException {
        createBaseUser("admin", "admin", "");
    }

    /**
     * Create common user
     */
    public static void createCommon() throws FtpException {
        createBaseUser("common", "common", "common");
    }

    /**
     * Crete ftp user that won't be in MongoDB
     * @param userName username
     * @param password password
     * @param homeDir home directory of user
     */
    private static void createBaseUser(String userName, String password, String homeDir) {
        try {
            PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
            userManagerFactory.setFile(new File(userFile));

            UserManager userManager = userManagerFactory.createUserManager();

        /* create basic user */
            BaseUser user = new BaseUser();
            user.setName(userName);
            user.setPassword(password);
            user.setHomeDirectory(ftpd + homeDir);

        /* give permissions */
            List<Authority> authorities = new ArrayList<>();
            authorities.add(new WritePermission());
            user.setAuthorities(authorities);

        /* save user */
            userManager.save(user);
            LOG.log(Level.INFO, "Admin user `" + userName + "` created.");
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Creates new dir, copies all files from old dir to new dir and deletes old dir
     * @param oldDir current directory
     * @param newDir new directory
     */
    private static void migrateAllFiles(String oldDir, String newDir) throws IOException {
        FileUtils.copyDirectory(new File(oldDir), new File(newDir));
        LOG.log(Level.INFO, "Copied directory + `" + oldDir + "` to new directory `" + newDir + "`");
        FileUtils.deleteDirectory(new File(oldDir));
        LOG.log(Level.INFO, "Deleted directory `" + oldDir + "`");
    }

    /**
     * Renames username and copies all it's files to dir of new user
     * @param oldUserName current username
     * @param newUserName new username
     */
    public static void renameUser(String oldUserName, String newUserName)
            throws FtpException, IOException, NewUserExistsException, UserNotFoundException,
            AdminEditException, InvalidNameException {

        /* lowercase usernames */
        oldUserName = oldUserName.toLowerCase();
        newUserName = newUserName.toLowerCase();

        UserAuthentication.checkAdmin(oldUserName, newUserName);
        /* validity of name will be checked at @createUser */

        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        userManagerFactory.setFile(new File(userFile));

        UserManager userManager = userManagerFactory.createUserManager();

        if (userExists(oldUserName, userManager)) {
            if (!userExists(newUserName, userManager)) {
            /* create basic user */
                User user = userManager.getUserByName(oldUserName);
                BaseUser newUser = new BaseUser();
                newUser.setName(newUserName);
                newUser.setHomeDirectory(ftpd + "/" + newUserName);
                newUser.setPassword(user.getPassword());

            /* save user */
                userManager.save(newUser);
                userManager.delete(oldUserName);
                LOG.log(Level.INFO, "User `" + oldUserName + "` migrated to user `" + newUserName + "`.");
                migrateAllFiles(user.getHomeDirectory(), newUser.getHomeDirectory());
            } else {
                throw new NewUserExistsException(newUserName);
            }
        } else {
            throw new UserNotFoundException(oldUserName);
        }
    }

    /**
     * Change password of FTP user. TODO: there ust be a better way of doing this that does not involve total rebuild
     * @param userName username of user changing it's password
     * @param oldPassword current password
     * @param newPassword new password
     */
    @SuppressWarnings({"unused", "unchecked"})
    public static void changePassword(String userName, String oldPassword, String newPassword)
            throws AdminEditException, FtpException, UserNotFoundException, IncorrectPasswordException {

        /* lowercase username */
        userName = userName.toLowerCase();

        UserAuthentication.checkAdmin(userName);

        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        userManagerFactory.setFile(new File(userFile));

        UserManager userManager = userManagerFactory.createUserManager();

        if (userExists(userName, userManager)) {
            User user = userManager.getUserByName(userName);
            if (user.getPassword().equals(oldPassword)) {
                throw new IncorrectPasswordException(userName);
            }
            BaseUser newUser = new BaseUser();
            newUser.setAuthorities((List<Authority>) user.getAuthorities());
            newUser.setName(user.getName());
            newUser.setPassword(newPassword);
            newUser.setHomeDirectory(user.getHomeDirectory());
            deleteUser(userName);
            userManager.save(newUser);
            LOG.log(Level.INFO, "Changed password of user `" + userName +  "`.");
        } else {
            throw new UserNotFoundException(userName);
        }
    }

    /**
     * Delete a FTP user
     * @param userName username of user to be deleted
     */
    public static void deleteUser(String userName) throws FtpException, UserNotFoundException, AdminEditException {

        /* lowercase usernames */
        userName = userName.toLowerCase();
        UserAuthentication.checkAdmin(userName);

        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        userManagerFactory.setFile(new File(userFile));

        UserManager userManager = userManagerFactory.createUserManager();

        if (userExists(userName, userManager)) {
            userManager.delete(userName);
        } else {
            throw new UserNotFoundException(userName);
        }
    }



    @SuppressWarnings("WeakerAccess")  /* for probable later use from outside class*/
    public static FtpServer getFtpServer(String configLocation, String beanName) {
        FileSystemXmlApplicationContext context = new FileSystemXmlApplicationContext(configLocation);
        context.setAllowBeanDefinitionOverriding(true);
        context.setBeanName("FTPServer");

        return context.getBean(beanName, FtpServer.class);
    }

    public static void main(String[] args) throws FtpException, InvalidNameException, AdminEditException {

        FtpServer ftpServer = FTPServer.getFtpServer(ftpLetFile, FTPlet.class.getSimpleName());
        FTPServer.init();
        ftpServer.start();
    }
}
