package client;

import chess.ChessGame;
import exception.ResponseException;
import server.ServerFacade;
import model.*;
import static ui.EscapeSequences.*;

import java.util.Arrays;

public class ChessClient {
    private String loggedInUser = null;
    private AuthData authData = null;
    private ChessGame.TeamColor playerColor = null;
    private final ServerFacade server;
    private ChessGameplay gameplay;
    private final String serverUrl;
    private State state = State.SIGNEDOUT;

    public ChessClient(String serverUrl) {
        server = new ServerFacade(serverUrl);
        this.serverUrl = serverUrl;
    }

    public String eval(String input) {
        try {
            var tokens = input.toLowerCase().split(" ");
            var cmd = (tokens.length > 0) ? tokens[0] : "help";
            var params = Arrays.copyOfRange(tokens, 1, tokens.length);

            if (getState().equals(State.GAMEPLAY) || getState().equals(State.OBSERVING)) {
                return gameplay.eval(tokens);
            }

            return switch(cmd) {
                case "login" -> login(params);
                case "register" -> register(params);
                case "create" -> createGame(params);
                case "list" -> listGames();
                case "join" -> joinGame(params);
                case "observe" -> observeGame(params);
                case "logout" -> logout();
                case "quit" -> "quit";
                default -> help();
            };
        } catch (ResponseException ex) {
            return ex.getMessage();
        }
    }

    public String login(String... params) throws ResponseException {
        if (params.length >= 2) {
            var username = params[0];
            var password = params[1];
            var userLogin = new LoginRequest(username, password);
            authData = server.login(userLogin);
            loggedInUser = username;
            state = State.SIGNEDIN;
            return String.format("Welcome %s", username);
        }
        throw new ResponseException(400, "Expected: <USERNAME> <PASSWORD>");
    }

    public String register(String... params) throws ResponseException {
        if (params.length >= 3) {
            var username = params[0];
            var password = params[1];
            var email = params[2];
            var newUser = new UserData(username, password, email);
            authData = server.register(newUser);
            loggedInUser = username;
            state = State.SIGNEDIN;
            return String.format("Account has been created for %s. You are now logged in", username);
        }
        throw new ResponseException(400, "Expected: <USERNAME> <PASSWORD> <EMAIL>");
    }

    public String createGame(String... params) throws ResponseException {
        assertSignedIn();
        if (params.length >= 1) {
            var name = params[0];
            var gameName = new GameName(name);
            var gameID = server.createGame(gameName, authData.authToken()).gameID();
            return String.format("Game '%s' has been created with ID: %d", gameName.gameName(), gameID);
        }
        throw new ResponseException(400, "Expected: <NAME>");
    }

    public String listGames() throws ResponseException {
        assertSignedIn();
        var games = server.listGames(authData.authToken());
        var result = new StringBuilder();

        if (games.games().isEmpty()) {
            result.append("No current games").append('\n');
            return result.toString();
        }

        for (var game : games.games()) {
            result.append(String.format("Game %s:", game.gameName())).append('\n');
            result.append(String.format("   ID: %d", game.gameID())).append('\n');
            String whiteUsername = (game.whiteUsername() != null) ? game.whiteUsername() : "[NONE]";
            String blackUsername = (game.blackUsername() != null) ? game.blackUsername() : "[NONE]";
            result.append(String.format("   White Username: %s", whiteUsername)).append('\n');
            result.append(String.format("   Black Username: %s", blackUsername)).append('\n');
        }
        return result.toString();
    }

    public String joinGame(String... params) throws ResponseException {
        assertSignedIn();
        if (params.length >= 1) {
            try {
                var gameID = Integer.parseInt(params[0]);
                String colorString = null;
                if (params.length == 2) {
                    colorString = params[1];
                    if (colorString.equalsIgnoreCase("white") || colorString.equalsIgnoreCase("black")) {
                        playerColor = (colorString.equalsIgnoreCase("white")) ? ChessGame.TeamColor.WHITE : ChessGame.TeamColor.BLACK;
                    }
                }
                JoinInformation joinInformation = new JoinInformation(playerColor.toString(), gameID);
                server.joinGame(joinInformation, authData.authToken());

                gameplay = new ChessGameplay(this, playerColor, serverUrl, authData, gameID);
                if (playerColor != null) {
                    gameplay.joinGame();
                    setState(State.GAMEPLAY);
                } else {
                    gameplay.observeGame();
                    setState(State.OBSERVING);
                }

                var joinedUser = (colorString != null) ? colorString : "observer";
                return String.format("Joined game %d as %s", gameID, joinedUser);
            } catch (NumberFormatException ignored) {
            }
        }
        throw new ResponseException(400, "Expected: <ID> [WHITE|BLACK|<empty>]");
    }

    public String observeGame(String... params) throws ResponseException {
        assertSignedIn();
        if (params.length >= 1) {
            try {
                var gameID = Integer.parseInt(params[0]);
                gameplay = new ChessGameplay(this, playerColor, serverUrl, authData, gameID);
                gameplay.observeGame();
                setState(State.OBSERVING);

                return String.format("Observing game %d", gameID);
            } catch (NumberFormatException ignored) {
            }
        }
        throw new ResponseException(400, "Expected: <ID>");
    }

    public String logout() throws ResponseException {
        assertSignedIn();
        var leavingUser = loggedInUser;
        loggedInUser = null;
        server.logout(authData.authToken());
        authData = null;
        state = State.SIGNEDOUT;
        return String.format("%s logged out successfully", leavingUser);
    }

    public String help() {
        if (state == State.SIGNEDOUT) {
            return SET_TEXT_COLOR_BLUE + "register <USERNAME> <PASSWORD> <EMAIL>" + SET_TEXT_COLOR_WHITE + " - to create an account\n" +
                SET_TEXT_COLOR_BLUE + "login <USERNAME> <PASSWORD>" + SET_TEXT_COLOR_WHITE + " - to play chess\n" +
                SET_TEXT_COLOR_BLUE + "quit" + SET_TEXT_COLOR_WHITE + " - playing chess\n" +
                SET_TEXT_COLOR_BLUE + "help" + SET_TEXT_COLOR_WHITE + " - with possible commands\n";
        }
        return SET_TEXT_COLOR_BLUE + "create <NAME>" + SET_TEXT_COLOR_WHITE + " - a game\n" +
            SET_TEXT_COLOR_BLUE + "list" + SET_TEXT_COLOR_WHITE + " - games\n" +
            SET_TEXT_COLOR_BLUE + "join <ID> [WHITE|BLACK|<empty>]" + SET_TEXT_COLOR_WHITE + " - a game\n" +
            SET_TEXT_COLOR_BLUE + "observe <ID>" + SET_TEXT_COLOR_WHITE + " - a game\n" +
            SET_TEXT_COLOR_BLUE + "logout" + SET_TEXT_COLOR_WHITE + " - when you are done\n" +
            SET_TEXT_COLOR_BLUE + "quit" + SET_TEXT_COLOR_WHITE + " - playing chess\n" +
            SET_TEXT_COLOR_BLUE + "help" + SET_TEXT_COLOR_WHITE + " - with possible commands\n";
    }

    public void setState(State newState) {
        state = newState;
    }

    public State getState() {
        return state;
    }

    private void assertSignedIn() throws ResponseException {
        if (state == State.SIGNEDOUT) {
            throw new ResponseException(400, "You must sign in.");
        }
    }
}
