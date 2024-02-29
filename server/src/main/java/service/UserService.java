package service;

import chess.ChessGame;
import dataAccess.DataAccessException;
import dataAccess.MemoryDataAccess;
import dataAccess.UnauthorizedException;
import model.AuthData;
import model.GameData;
import model.UserData;

import java.util.Collection;

public class UserService {
    private final MemoryDataAccess dataAccess;

    public UserService(MemoryDataAccess dataAccess) {
        this.dataAccess = dataAccess;
    }

    public AuthData register(UserData user) throws DataAccessException, UnauthorizedException {
        var existingUser = dataAccess.getUser(user.username());

        if (existingUser == null) {
            dataAccess.createUser(user);
            return dataAccess.createAuth(user);
        } else {
            throw new UnauthorizedException("Username already exists in the database.");
        }
    }

    public AuthData login(String username, String password) throws DataAccessException, UnauthorizedException {
        var existingUser = dataAccess.getUser(username);

        if (existingUser == null) {
            throw new UnauthorizedException("Username has not been registered.");
        } else if (!existingUser.password().equals(password)) {
            throw new DataAccessException("Incorrect password.");
        } else {
            return dataAccess.createAuth(existingUser);
        }
    }

    public void logout(String authToken) throws UnauthorizedException {
        AuthData authData = dataAccess.getAuth(authToken);
        if (authData == null) {
            throw new UnauthorizedException("User is not registered with the system.");
        }

        dataAccess.deleteAuth(authData);
    }

    public Collection<GameData> listGames(String authToken) throws UnauthorizedException {
        AuthData authData = dataAccess.getAuth(authToken);
        if (authData == null) {
            throw new UnauthorizedException("User is not registered with the system.");
        }

        return dataAccess.getGames();
    }

    public int createGame(String authToken, String gameName) throws DataAccessException, UnauthorizedException {
        AuthData authData = dataAccess.getAuth(authToken);
        if (authData == null) {
            throw new UnauthorizedException("User is not registered with the system.");
        }

        int newGameID = dataAccess.createGameId(gameName);
        ChessGame game = new ChessGame();
        dataAccess.createGame(new GameData(newGameID, null, null, gameName, game));

        return newGameID;
    }

    public void joinGame(String authToken, String playerColor, Integer gameID) throws DataAccessException, UnauthorizedException {
        AuthData authData = dataAccess.getAuth(authToken);
        if (authData == null) {
            throw new UnauthorizedException("User is not registered with the system.");
        }

        var username = dataAccess.getAuth(authToken).username();

        dataAccess.joinGame(username, playerColor, gameID);
    }

    public void clearApplication() {
        dataAccess.clearUsers();
        dataAccess.clearGames();
        dataAccess.clearAuthTokens();
    }
}
