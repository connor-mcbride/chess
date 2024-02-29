package serviceTests;

import chess.ChessGame;
import dataAccess.DataAccessException;
import dataAccess.MemoryDataAccess;
import dataAccess.UnauthorizedException;
import model.UserData;
import model.AuthData;
import model.GameData;
import org.junit.jupiter.api.Test;
import service.UserService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DataAccessTest {
    MemoryDataAccess dataAccess = getDataAccess();
    UserService service = new UserService(dataAccess);
    private static MemoryDataAccess getDataAccess() {
        return new MemoryDataAccess();
    }

    @Test
    void registerUser() throws DataAccessException, UnauthorizedException {
        UserData testUser = new UserData("stevejobs", "apple", "steve@icloud.com");

        AuthData authData = service.register(testUser);
        var users = dataAccess.getUsers();
        assertEquals(1, users.size());
        assertTrue(users.containsValue(testUser));
        assertEquals(1, dataAccess.getAuths().size());
        assertTrue(dataAccess.getAuths().containsValue(authData));
    }

    @Test
    void registerExistingUser() throws DataAccessException, UnauthorizedException {
        UserData testUser = new UserData("stevejobs", "apple", "steve@icloud.com");
        service.register(testUser);

        try {
            service.register(testUser);
            fail("Service registered an existing user");
        } catch (UnauthorizedException e) {
            assertEquals(1, dataAccess.getUsers().size());
            assertEquals(1, dataAccess.getAuths().size());
        }
    }

    @Test
    void loginUser() throws DataAccessException, UnauthorizedException {
        UserData testUser = new UserData("lebron", "leking", "lebron@aol.com");
        service.register(testUser);

        AuthData authData = service.login(testUser.username(), testUser.password());
        assertEquals(1, dataAccess.getAuths().size());
        assertTrue(dataAccess.getAuths().containsValue(authData));
    }

    @Test
    void loginNonExistentUser() throws DataAccessException {
        assertEquals(0, dataAccess.getAuths().size());
        UserData testUser = new UserData("lebron", "leking", "lebron@aol.com");

        try {
            service.login(testUser.username(), testUser.password());
            fail("User has not been registered first.");
        } catch (UnauthorizedException e) {
            assertEquals(0, dataAccess.getAuths().size());
        }
    }

    @Test
    void logoutUser() throws DataAccessException, UnauthorizedException {
        UserData testUser = new UserData("Bob", "Marley", "bobmarley@yahoo.com");
        service.register(testUser);
        AuthData testAuth = service.login(testUser.username(), testUser.password());

        service.logout(testAuth.authToken());
        assertEquals(0, dataAccess.getAuths().size());
    }

    @Test
    void logoutNonExistentUser() {
        try {
            service.logout(UUID.randomUUID().toString());
            fail("Service shouldn't log out user who isn't logged in.");
        } catch (UnauthorizedException e) {
            assertEquals(0, dataAccess.getAuths().size());
        }
    }

    @Test
    void listGames() throws DataAccessException, UnauthorizedException {
        UserData testUser = new UserData("Bob", "Marley", "bobmarley@yahoo.com");
        AuthData testAuth = service.register(testUser);
        service.createGame(testAuth.authToken(), "game1");

        UserData testUserTwo = new UserData("lebron", "leking", "lebron@aol.com");
        AuthData testAuthTwo = service.register(testUserTwo);
        service.createGame(testAuthTwo.authToken(), "game2");

        var gameList = service.listGames(testAuth.authToken());
        assertEquals(2, gameList.size());
        assertEquals(gameList.size(), dataAccess.getGames().size());
    }

    @Test
    void listGamesNotAuthorized() {
        try {
            service.listGames(UUID.randomUUID().toString());
            fail("Service shouldn't list games for user who isn't registered.");
        } catch (UnauthorizedException e) {
            assertEquals(0, dataAccess.getAuths().size());
        }
    }

    @Test
    void createGame() throws DataAccessException, UnauthorizedException {
        UserData testUser = new UserData("Bob", "Marley", "bobmarley@yahoo.com");
        AuthData testAuth = service.register(testUser);
        service.createGame(testAuth.authToken(), "game1");

        assertEquals(1, dataAccess.getGames().size());
    }

    @Test
    void createGameNotAuthorized() throws DataAccessException {
        try {
            service.createGame(UUID.randomUUID().toString(), "game1");
            fail("Service shouldn't log out user who isn't logged in.");
        } catch (UnauthorizedException e) {
            assertEquals(0, dataAccess.getAuths().size());
        }
    }

    @Test
    void joinGame() throws DataAccessException, UnauthorizedException {
        UserData testUser = new UserData("Steve", "Martin", "steve@gmail.com");
        AuthData testAuth = service.register(testUser);

        service.createGame(testAuth.authToken(), "game1");
        service.joinGame(testAuth.authToken(), "WHITE", 1);
    }

    @Test
    void joinGameNotAuthorized() throws DataAccessException {
        try {
            var testAuth = new AuthData(UUID.randomUUID().toString(), "carlos");
            service.createGame(testAuth.authToken(), "game1");
            service.joinGame(testAuth.authToken(), "WHITE", 1);

            fail("Service shouldn't allow user to join game who isn't logged in.");
        } catch (UnauthorizedException e) {
            assertEquals(0, dataAccess.getGames().size());
        }
    }

    @Test
    void joinGameWrongID() throws UnauthorizedException {
        try {
            UserData testUser = new UserData("Steve", "Martin", "steve@gmail.com");
            AuthData testAuth = service.register(testUser);
            service.createGame(testAuth.authToken(), "game1");
            service.joinGame(testAuth.authToken(), "BLACK", 2);

            fail("Service shouldn't allow user to join game with invalid ID.");
        } catch (DataAccessException e) {
            assertEquals(1, dataAccess.getGames().size());
        }
    }

    @Test
    void clearApplication() {
        UserData testUser = new UserData("steve01", "password", "steve@gmail.com");
        dataAccess.createUser(testUser);
        dataAccess.createAuth(testUser);

        String gameName = "test";
        Integer gameID = dataAccess.createGameId(gameName);
        ChessGame game = new ChessGame();
        dataAccess.createGame(new GameData(gameID, "white", "black", gameName, game));

        service.clearApplication();
        assertEquals(0, dataAccess.getGames().size());
        assertEquals(0, dataAccess.getAuths().size());
        assertEquals(0, dataAccess.getUsers().size());
    }

    @Test
    void clearEmptyApplication() {
        // Verify that database is already empty
        assertEquals(0, dataAccess.getGames().size());
        assertEquals(0, dataAccess.getAuths().size());
        assertEquals(0, dataAccess.getUsers().size());

        // Verify that clearing empty tables doesn't throw an error
        clearApplication();
    }
}