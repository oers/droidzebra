package de.earthlingz.oerszebra;

import android.util.Log;
import com.shurik.droidzebra.*;

import java.util.LinkedList;
import java.util.Locale;

public class DroidZebraHandler implements ZebraEngineMessageHandler, GameMessageReceiver {

    private BoardState boardState;
    private ZebraEngine mZebraThread;
    private GameController controller;

    private android.os.Handler handler = new android.os.Handler();

    DroidZebraHandler(BoardState boardState, GameController controller, ZebraEngine mZebraThread) {
        this.controller = controller;

        this.boardState = boardState;
        this.mZebraThread = mZebraThread;
    }

    @Override
    public void onError(String error) {
        controller.showAlertDialog(error);
        mZebraThread.setInitialGameState(new LinkedList<>());
    }

    @Override
    public void onDebug(String debug) {
        Log.v("OersZebra", debug);
    }

    @Override
    public void onBoard(ZebraBoard zebraBoard) {
        String score;
        int sideToMove = zebraBoard.getSideToMove();

        //triggers animations
        boolean boardChanged = boardState.updateBoard(zebraBoard.getBoard());

        boardState.setBlackScore(zebraBoard.getBlackPlayer().getDiscCount());
        boardState.setWhiteScore(zebraBoard.getWhitePlayer().getDiscCount());

        if (sideToMove == ZebraEngine.PLAYER_BLACK) {
            score = String.format(Locale.getDefault(), "•%d", boardState.getBlackScore());
        } else {
            score = String.format(Locale.getDefault(), "%d", boardState.getBlackScore());
        }
        controller.getStatusView().setTextForID(
                StatusView.ID_SCORE_BLACK,
                score
        );

        if (sideToMove == ZebraEngine.PLAYER_WHITE) {
            score = String.format(Locale.getDefault(), "%d•", boardState.getWhiteScore());
        } else {
            score = String.format(Locale.getDefault(), "%d", boardState.getWhiteScore());
        }
        controller.getStatusView().setTextForID(
                StatusView.ID_SCORE_WHITE,
                score
        );

        int iStart, iEnd;
        byte[] black_moves = zebraBoard.getBlackPlayer().getMoves();
        byte[] white_moves = zebraBoard.getWhitePlayer().getMoves();

        iEnd = black_moves.length;
        iStart = Math.max(0, iEnd - 4);
        for (int i = 0; i < 4; i++) {
            String num_text = String.format(Locale.getDefault(), "%d", i + iStart + 1);
            String move_text;
            if (i + iStart < iEnd) {
                Move move = new Move(black_moves[i + iStart]);
                move_text = move.getText();
            } else {
                move_text = "";
            }
            controller.getStatusView().setTextForID(
                    StatusView.ID_SCORELINE_NUM_1 + i,
                    num_text
            );
            controller.getStatusView().setTextForID(
                    StatusView.ID_SCORELINE_BLACK_1 + i,
                    move_text
            );
        }

        iEnd = white_moves.length;
        iStart = Math.max(0, iEnd - 4);
        for (int i = 0; i < 4; i++) {
            String move_text;
            if (i + iStart < iEnd) {
                Move move = new Move(white_moves[i + iStart]);
                move_text = move.getText();
            } else {
                move_text = "";
            }
            controller.getStatusView().setTextForID(
                    StatusView.ID_SCORELINE_WHITE_1 + i,
                    move_text
            );
        }

        byte move = (byte) zebraBoard.getLastMove();
        boardState.setLastMove(move == Move.PASS ? null : new Move(move));

        byte moveNext = (byte) zebraBoard.getNextMove();
        boardState.setNextMove(moveNext == Move.PASS ? null : new Move(moveNext));


        CandidateMove[] candidateMoves = zebraBoard.getCandidateMoves();

        boardState.setMoves(candidateMoves);

        if (controller.getStatusView() != null && zebraBoard.getOpening() != null) {
            controller.getStatusView().setTextForID(
                    StatusView.ID_STATUS_OPENING,
                    zebraBoard.getOpening()
            );
        }
        if (boardChanged) {
            Log.v("Handler", "BoardChanged");
            controller.getBoardView().onBoardStateChanged();

        } else {
            Log.v("Handler", "invalidate");
            controller.getBoardView().invalidate();
        }
    }

    @Override
    public void onPass() {
        controller.showPassDialog();
    }

    @Override
    public void onGameStart() {
        // noop
    }

    @Override
    public void onGameOver() {
        controller.setCandidateMoves(new CandidateMove[]{});
        int max = boardState.getBoard().length * boardState.getBoard().length;
        if (boardState.getBlackScore() + boardState.getWhiteScore() < max) {
            //adjust result
            if (boardState.getBlackScore() > boardState.getWhiteScore()) {
                boardState.setBlackScore(max - boardState.getWhiteScore());
            } else {
                boardState.setWhiteScore(max - boardState.getBlackScore());
            }
        }
        controller.showGameOverDialog();
    }

    @Override
    public void onMoveStart() {

    }

    @Override
    public void onMoveEnd() {
        controller.dismissBusyDialog();
        if (controller.isHintUp()) {
            controller.setHintUp(false);
            mZebraThread.setPracticeMode(controller.isPracticeMode());
            mZebraThread.sendSettingsChanged();
        }

    }

    @Override
    public void onEval(String eval) {
        if (controller.getSettingDisplayPV()) {
            controller.getStatusView().setTextForID(
                    StatusView.ID_STATUS_EVAL,
                    eval
            );
        }
    }

    @Override
    public void onPv(byte[] pv) {
        if (controller.getSettingDisplayPV() && pv != null) {
            StringBuilder pvText = new StringBuilder();
            for (byte move : pv) {
                pvText.append(new Move(move).getText());
                pvText.append(" ");
            }
            controller.getStatusView().setTextForID(
                    StatusView.ID_STATUS_PV,
                    pvText.toString()
            );
        }

    }

    @Override
    public void sendError(String error) {
        handler.post(() -> onError(error));
    }

    @Override
    public void sendDebug(String debug) {
        handler.post(() -> onDebug(debug));
    }

    @Override
    public void sendBoard(ZebraBoard board) {
        handler.post(() -> onBoard(board));
    }

    @Override
    public void sendPass() {
        handler.post(this::onPass);
    }

    @Override
    public void sendGameStart() {
        handler.post(this::onGameStart);
    }

    @Override
    public void sendGameOver() {
        handler.post(this::onGameOver);
    }

    @Override
    public void sendMoveStart() {
        handler.post(this::onMoveStart);
    }

    @Override
    public void sendMoveEnd() {
        handler.post(this::onMoveEnd);

    }

    @Override
    public void sendEval(String eval) {
        handler.post(() -> onEval(eval));
    }

    @Override
    public void sendPv(byte[] moves) {
        handler.post(() -> onPv(moves));
    }
}