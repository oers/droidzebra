package de.earthlingz.oerszebra;

import com.shurik.droidzebra.*;


/**
 * Uses Android Handler to proxy GameState changes to Thread on which is created
 */
public class GameStateHandlerProxy implements GameStateListener {

    private GameMessageReceiver receiver;
    private android.os.Handler handler = new android.os.Handler();

    GameStateHandlerProxy(GameMessageReceiver receiver) {
        this.receiver = receiver;
    }

    @Override
    public void sendBoard(GameState board) {
        handler.post(() -> receiver.onBoard(board));
    }

    @Override
    public void sendPass() {
        handler.post(receiver::onPass);
    }

    @Override
    public void sendGameStart() {
        handler.post(receiver::onGameStart);
    }

    @Override
    public void sendGameOver() {
        handler.post(receiver::onGameOver);
    }

    @Override
    public void sendMoveStart() {
        handler.post(receiver::onMoveStart);
    }

    @Override
    public void sendMoveEnd() {
        handler.post(receiver::onMoveEnd);

    }

    @Override
    public void sendEval(String eval) {
        handler.post(() -> receiver.onEval(eval));
    }

    @Override
    public void sendPv(byte[] moves) {
        handler.post(() -> receiver.onPv(moves));
    }
}