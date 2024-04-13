package client;

import chess.*;
import client.webSocket.NotificationHandler;
import client.webSocket.WebSocketFacade;
import com.google.gson.Gson;
import exception.ResponseException;
import model.AuthData;
import webSocketMessages.serverMessages.LoadGame;
import webSocketMessages.serverMessages.Error;
import webSocketMessages.serverMessages.Notification;
import webSocketMessages.serverMessages.ServerMessage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static ui.EscapeSequences.*;

public class ChessGameplay implements NotificationHandler {
    private ChessGame gameState;
    private final ChessGame.TeamColor playerColor;
    private final ChessClient client;
    private final WebSocketFacade ws;
    private final AuthData authData;
    private final int gameID;

    public ChessGameplay(ChessClient client, ChessGame.TeamColor playerColor, String serverUrl, AuthData authData, int gameID) throws ResponseException {
        this.playerColor = playerColor;
        this.client = client;
        ws = new WebSocketFacade(serverUrl, this);
        this.authData = authData;
        this.gameID = gameID;
    }

    public String eval(String... tokens) throws ResponseException {
        var cmd = (tokens.length > 0) ? tokens[0] : "help";
        var params = Arrays.copyOfRange(tokens, 1, tokens.length);

        return switch(cmd) {
            case "redraw" -> redrawBoard();
            case "leave" -> leaveGame(params);
            case "move" -> makeMove(params);
            case "resign" -> resignGame();
            case "valid" -> legalMoves(params);
            default -> help();
        };
    }

    private String getLabel(int row, int col) {
        String[] columnLabels = {" ", "a", "b", "c", "d", "e", "f", "g", "h", " "};
        String[] rowLabels = {" ", "8", "7", "6", "5", "4", "3", "2", "1", " "};
        if (row == 0 || row == 9) {
            return columnLabels[col];
        } else if (col == 0 || col == 9) {
            return rowLabels[row];
        }

        ChessBoard board = getGameState().getBoard();
        ChessPiece tempPiece = board.getPiece(new ChessPosition(row, col));
        if (tempPiece == null) {
            return " ";
        }

        return switch (tempPiece.getPieceType()) {
            case ROOK -> "R";
            case KING -> "K";
            case QUEEN -> "Q";
            case BISHOP -> "B";
            case KNIGHT -> "N";
            case PAWN -> "P";
        };
    }


    private String chessCharacterLookup(int row, int col) {
        String piece = getLabel(row, col);
        if (row == 0 || col == 0 || row == 9 || col == 9) {
            return piece;  // Early return for borders, which do not have pieces
        }

        ChessPosition newPosition = new ChessPosition(row, col);
        ChessPiece pieceAtPosition = getGameState().getBoard().getPiece(newPosition);
        if (pieceAtPosition == null) {
            return piece;  // Return the piece character, which should be " " for empty squares
        }

        if (pieceAtPosition.getTeamColor().equals(ChessGame.TeamColor.WHITE)) {
            return SET_TEXT_COLOR_WHITE + piece + RESET_TEXT_COLOR;
        } else {
            return SET_TEXT_COLOR_BLACK + piece + RESET_TEXT_COLOR;
        }
    }


    public String boardLayout(ChessGame.TeamColor playerColor, boolean highlight, ChessPosition position) {
        var board = new StringBuilder();
        for (int row = 0; row <= 9; row++) {
            for (int col = 0; col <= 9; col++) {
                int newRow = (Objects.equals(playerColor, ChessGame.TeamColor.WHITE)) ? row : 9 - row;
                int newCol = (Objects.equals(playerColor, ChessGame.TeamColor.WHITE)) ? col : 9 - col;
                String chessCharacter = chessCharacterLookup(newRow, newCol);
                if (row == 0 || col == 0 || row == 9 || col == 9) {
                    board.append(SET_BG_COLOR_LIGHT_GREY);
                } else {
                    boolean isDark = (row + col) % 2 == 1;
                    String bgColor;

                    if (highlight) {
                        var validMoves = getGameState().validMoves(position);
                        boolean containsSquare = false;
                        for (var move : validMoves) {
                            if (move.getEndPosition().equals(new ChessPosition(row, col))) {
                                containsSquare = true;
                                break;
                            }
                        }

                        if (containsSquare) {
                            bgColor = isDark ? SET_BG_COLOR + "32m" : SET_BG_COLOR + "92m";
                        } else {
                            bgColor = isDark ? SET_BG_COLOR + "95m" : SET_BG_COLOR + "222m";
                        }
                    } else {
                        bgColor = isDark ? SET_BG_COLOR + "95m" : SET_BG_COLOR + "222m";
                    }

                    board.append(bgColor);
                }
                board.append(String.format(" %s ", chessCharacter)).append("\u001B[49m");
            }
            board.append('\n').append(SET_TEXT_COLOR_WHITE).append("\u001B[49m");
        }
        return board.toString();
    }

    public void joinGame() throws ResponseException {
        ws.joinGame(gameID, playerColor, authData);
    }

    public void observeGame() throws ResponseException {
        ws.observeGame(gameID, authData);
        client.setState(State.OBSERVING);
    }

    public String redrawBoard() {
        return boardLayout(playerColor, false, null);
    }

    public String leaveGame(String... params) throws ResponseException {
        if (params.length >= 1) {
            int gameID = Integer.parseInt(params[0]);
            ws.leaveGame(gameID, authData);
            client.setState(State.SIGNEDIN);

            return String.format("%s has left game %d", authData.username(), gameID);
        }
        throw new ResponseException(400, "Expected: <GAME_ID>");
    }

    private ChessPosition generatePosition(String position) throws ResponseException {
        try {
            char columnChar = Character.toLowerCase(position.charAt(0));
            char rowChar = position.charAt(1);
            int col = columnChar - 'a' + 1;
            int row = rowChar - '1' + 1;

            return new ChessPosition(row, col);
        } catch (Exception ex) {
            throw new ResponseException(400, ex.getMessage());
        }
    }

    public String makeMove(String... params) throws ResponseException {
        if (params.length >= 1) {
            String move = params[0];
            if (move.length() >= 4) {
                String startPos = move.substring(0, 2);
                String endPos = move.substring(2, 4);
                Map<String, ChessPiece.PieceType> notationPieces = new HashMap<>() {{
                    put("Q", ChessPiece.PieceType.QUEEN); put("N", ChessPiece.PieceType.KNIGHT);
                    put("R", ChessPiece.PieceType.KNIGHT); put("B", ChessPiece.PieceType.BISHOP);
                }};

                var promotionPiece = (params.length >= 2) ? notationPieces.get(params[1].toUpperCase()) : null;
                var startPosition = generatePosition(startPos);
                var endPosition = generatePosition(endPos);
                var newMove = new ChessMove(startPosition, endPosition, promotionPiece);
                ws.makeMove(gameID, newMove, authData);

                String movingPiece = gameState.getBoard().getPiece(startPosition).toString();
                return String.format("Moved %s %s to %s", startPos, movingPiece, endPos);
            }
        }
        throw new ResponseException(400, "Expected: <START_POSITION><END_POSITION> <PROMOTION_PIECE (opt.)>");
    }

    public String resignGame() throws ResponseException {
        ws.resign(gameID, authData);
        client.setState(State.SIGNEDIN);

        return String.format("%s has resigned game %d", authData.username(), gameID);
    }

    public String legalMoves(String... params) throws ResponseException {
        if (params.length >= 1) {
            var givenPosition = params[0];
            var startingPosition = generatePosition(givenPosition);
            return boardLayout(playerColor, true, startingPosition);
        }
        throw new ResponseException(400, "Expected: <POSITION>");
    }

    private void setGameState(ChessGame newGame) {
        gameState = newGame;
    }

    private ChessGame getGameState() {
        return gameState;
    }

    public String help() {
        return SET_TEXT_COLOR_BLUE + "redraw" + SET_TEXT_COLOR_WHITE + " - to display current board state\n" +
                SET_TEXT_COLOR_BLUE + "leave <GAME_ID>" + SET_TEXT_COLOR_WHITE + " - to leave the game\n" +
                SET_TEXT_COLOR_BLUE + "move <START_POSITION><END_POSITION> <PROMOTION_PIECE (opt.)>" + SET_TEXT_COLOR_WHITE + " - to move one of your pieces\n" +
                SET_TEXT_COLOR_BLUE + "resign" + SET_TEXT_COLOR_WHITE + " - to forfeit the game\n" +
                SET_TEXT_COLOR_BLUE + "valid <POSITION>" + SET_TEXT_COLOR_WHITE + " - to show possible moves for a certain position\n" +
                SET_TEXT_COLOR_BLUE + "help" + SET_TEXT_COLOR_WHITE + " - with possible commands\n";
    }

    @Override
    public void notify(String message) {
        ServerMessage serverMessage = new Gson().fromJson(message, ServerMessage.class);
        ServerMessage.ServerMessageType messageType = serverMessage.getServerMessageType();
        switch (messageType) {
            case LOAD_GAME -> {
                LoadGame loadGame = new Gson().fromJson(message, LoadGame.class);
                setGameState(loadGame.getGame());
                System.out.println();
                System.out.println(boardLayout(playerColor, false, null));
            }
            case ERROR -> {
                Error error = new Gson().fromJson(message, Error.class);
                System.out.println(SET_TEXT_COLOR_GREEN + error.getErrorMessage());
            }
            case NOTIFICATION -> {
                Notification notification = new Gson().fromJson(message, Notification.class);
                System.out.println(SET_TEXT_COLOR_GREEN + notification.getMessage());
            }
        }
    }
}
