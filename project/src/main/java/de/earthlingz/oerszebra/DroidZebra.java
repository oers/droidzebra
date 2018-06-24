/* Copyright (C) 2010 by Alex Kompel  */
/* This file is part of DroidZebra.

	DroidZebra is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	DroidZebra is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with DroidZebra.  If not, see <http://www.gnu.org/licenses/>
*/

package de.earthlingz.oerszebra;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Objects;
import com.shurik.droidzebra.CandidateMove;
import com.shurik.droidzebra.EngineError;
import com.shurik.droidzebra.InvalidMove;
import com.shurik.droidzebra.Move;
import com.shurik.droidzebra.PlayerInfo;
import com.shurik.droidzebra.ZebraBoard;
import com.shurik.droidzebra.ZebraEngine;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

import de.earthlingz.oerszebra.parser.Gameparser;

import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_ZEBRA_BLACK;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_ZEBRA_VS_ZEBRA;
import static de.earthlingz.oerszebra.GameSettingsConstants.FUNCTION_ZEBRA_WHITE;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.DEFAULT_SETTING_SENDMAIL;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.RANDOMNESS_HUGE;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.RANDOMNESS_LARGE;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.RANDOMNESS_MEDIUM;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.RANDOMNESS_NONE;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.RANDOMNESS_SMALL;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.SETTINGS_KEY_FUNCTION;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.SETTINGS_KEY_SENDMAIL;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.SHARED_PREFS_NAME;

//import android.util.Log;

public class DroidZebra extends FragmentActivity implements GameController, SharedPreferences.OnSharedPreferenceChangeListener
{
	private ClipboardManager clipboard;
	private ZebraEngine mZebraThread;


	private boolean mBusyDialogUp = false;
	private boolean mHintIsUp = false;
	private boolean mIsInitCompleted = false;
	private boolean mActivityActive = false;

	private BoardView mBoardView;
	private StatusView mStatusView;

	private BoardState state = ZebraServices.getBoardState();

	private Gameparser parser;
    private WeakReference<AlertDialog> alert = null;

    public GlobalSettingsLoader settingsLoader;

	public DroidZebra() {
		super();
		this.setGameParser(ZebraServices.getGameParser());
	}



	public boolean isThinking() {
		return mZebraThread.isThinking();
	}

	public boolean isHumanToMove() {
		return mZebraThread.isHumanToMove();
	}

	public void makeMove(Move mMoveSelection) throws InvalidMove {
		mZebraThread.makeMove(mMoveSelection);
	}

	void setBoardState(@NonNull BoardState state) {
		this.state = state;
	}

	void setGameParser(Gameparser parser) {
		this.parser = parser;
	}

	private void newCompletionPort(final int zebraEngineStatus, final Runnable completion) {
		new CompletionAsyncTask(zebraEngineStatus, completion, getEngine())
		.execute();
	}

	public FieldState[][] getBoard() {
		return state.getmBoard();
	}



	public ZebraEngine getEngine() {
		return mZebraThread;
	}

	public void initBoard() {
		state.reset();
		if (mStatusView != null)
			mStatusView.clear();
	}

	public CandidateMove[] getCandidateMoves() {
		return state.getMoves();
	}

	public void setCandidateMoves(CandidateMove[] cmoves) {
		state.setMoves(cmoves);
		runOnUiThread(() -> mBoardView.invalidate());
	}

	public boolean evalsDisplayEnabled() {
		return  settingsLoader.mSettingZebraPracticeMode || mHintIsUp;
	}

	public void newGame() {
		if(mZebraThread.getEngineState()!=ZebraEngine.ES_READY2PLAY) {
			mZebraThread.stopGame();
		}
		newCompletionPort(
				ZebraEngine.ES_READY2PLAY,
				() -> {
					DroidZebra.this.initBoard();
					DroidZebra.this.loadSettings();
					DroidZebra.this.mZebraThread.setEngineState(ZebraEngine.ES_PLAY);
				}
		);
	}

	/* Creates the menu items */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.menu, menu);
	    return super.onCreateOptionsMenu(menu);
	}

	public boolean initialized() {
		return mIsInitCompleted;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if( !mIsInitCompleted ) return false;
		switch (item.getItemId()) {
			case R.id.menu_new_game:
				newGame();
				return true;
			case R.id.menu_quit:
				showQuitDialog();
				return true;
			case R.id.menu_take_back:
				mZebraThread.undoMove();
				return true;
			case R.id.menu_take_redo:
				mZebraThread.redoMove();
				return true;
			case R.id.menu_settings: {
				// Launch Preference activity
				Intent i = new Intent(this, SettingsPreferences.class);
				startActivity(i);
			}
			return true;
			case R.id.menu_switch_sides: {
				switchSides();
			}
			break;
			case R.id.menu_enter_moves: {
				enterMoves();
			}
			break;
			case R.id.menu_mail: {
				sendMail();
			}
			return true;
			case R.id.menu_hint: {
				showHint();
			}
			return true;
		}
		return false;
	}

	@Override
	protected void onNewIntent(Intent intent) {

		String action = intent.getAction();
		String type = intent.getType();

		Log.i("Intent", type + " " + action);

		if (Intent.ACTION_SEND.equals(action) && type != null) {
			if ("text/plain".equals(type)) {
				setUpBoard(intent.getDataString()); // Handle text being sent
			} else 	if ("message/rfc822".equals(type)) {
				Log.i("Intent", intent.getStringExtra(Intent.EXTRA_TEXT));
				setUpBoard(intent.getStringExtra(Intent.EXTRA_TEXT)); // Handle text being sent
			}
			else  {
				Log.e("intent", "unknown intent");
			}
		} else {
			Log.e("intent", "unknown intent");
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		initBoard();

		clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

		setContentView(R.layout.spash_layout);
		new ActionBarHelper(this).hide();

        mZebraThread = new ZebraEngine(new AndroidContext(this));
        mZebraThread.setHandler(new DroidZebraHandler(state, this, mZebraThread));

        this.settingsLoader = new GlobalSettingsLoader(this);
		// preferences
		SharedPreferences mSettings = getSharedPreferences(SHARED_PREFS_NAME, 0);
		mSettings.registerOnSharedPreferenceChangeListener(this);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        Log.i("Intent", type + " " + action);

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type) || "message/rfc822".equals(type)) {
				mZebraThread.setInitialGameState(parser.makeMoveList(intent.getStringExtra(Intent.EXTRA_TEXT)));
			}
            else  {
                Log.e("intent", "unknown intent");
            }
        } else 	if( savedInstanceState != null
                && savedInstanceState.containsKey("moves_played_count")
                && savedInstanceState.getInt("moves_played_count") > 0) {
            Log.i("moves_play_count", String.valueOf(savedInstanceState.getInt("moves_played_count")));
            Log.i("moves_played", String.valueOf(savedInstanceState.getInt("moves_played")));
            mZebraThread.setInitialGameState(savedInstanceState.getInt("moves_played_count"), savedInstanceState.getByteArray("moves_played"));
        }

		mZebraThread.start();

		newCompletionPort(
				ZebraEngine.ES_READY2PLAY,
				() -> {
					DroidZebra.this.setContentView(R.layout.board_layout);
					new ActionBarHelper(DroidZebra.this).show();
					DroidZebra.this.mBoardView = (BoardView) DroidZebra.this.findViewById(R.id.board);
					DroidZebra.this.mStatusView = (StatusView) DroidZebra.this.findViewById(R.id.status_panel);
					DroidZebra.this.mBoardView.setDroidZebra(DroidZebra.this);
					DroidZebra.this.mBoardView.requestFocus();
					DroidZebra.this.initBoard();
					DroidZebra.this.loadSettings();
					DroidZebra.this.mZebraThread.setEngineState(ZebraEngine.ES_PLAY);
					DroidZebra.this.mIsInitCompleted = true;
				}
		);
	}

	private void loadSettings() {
		boolean bZebraSettingChanged = this.settingsLoader.loadSettings();
		try {
			mZebraThread.setAutoMakeMoves (settingsLoader.mSettingAutoMakeForcedMoves);
			mZebraThread.setForcedOpening (settingsLoader.mSettingZebraForceOpening);
			mZebraThread.setHumanOpenings (settingsLoader.mSettingZebraHumanOpenings);
			mZebraThread.setPracticeMode (settingsLoader.mSettingZebraPracticeMode);
			mZebraThread.setUseBook (settingsLoader.mSettingZebraUseBook);

			switch(  settingsLoader.mSettingFunction ) {
				case FUNCTION_HUMAN_VS_HUMAN:
					mZebraThread.setPlayerInfo(new PlayerInfo(ZebraEngine.PLAYER_BLACK, 0, 0, 0, ZebraEngine.INFINIT_TIME, 0));
					mZebraThread.setPlayerInfo(new PlayerInfo(ZebraEngine.PLAYER_WHITE, 0, 0, 0, ZebraEngine.INFINIT_TIME, 0));
					break;
				case FUNCTION_ZEBRA_BLACK:
					mZebraThread.setPlayerInfo(new PlayerInfo(ZebraEngine.PLAYER_BLACK,  settingsLoader.mSettingZebraDepth,  settingsLoader.mSettingZebraDepthExact,  settingsLoader.mSettingZebraDepthWLD, ZebraEngine.INFINIT_TIME, 0));
					mZebraThread.setPlayerInfo(new PlayerInfo(ZebraEngine.PLAYER_WHITE, 0, 0, 0, ZebraEngine.INFINIT_TIME, 0));
					break;
				case FUNCTION_ZEBRA_VS_ZEBRA:
					mZebraThread.setPlayerInfo(new PlayerInfo(ZebraEngine.PLAYER_BLACK,  settingsLoader.mSettingZebraDepth,  settingsLoader.mSettingZebraDepthExact,  settingsLoader.mSettingZebraDepthWLD, ZebraEngine.INFINIT_TIME, 0));
					mZebraThread.setPlayerInfo(new PlayerInfo(ZebraEngine.PLAYER_WHITE,  settingsLoader.mSettingZebraDepth,  settingsLoader.mSettingZebraDepthExact,  settingsLoader.mSettingZebraDepthWLD, ZebraEngine.INFINIT_TIME, 0));
					break;
				case FUNCTION_ZEBRA_WHITE:
				default:
					mZebraThread.setPlayerInfo(new PlayerInfo(ZebraEngine.PLAYER_BLACK, 0, 0, 0, ZebraEngine.INFINIT_TIME, 0));
					mZebraThread.setPlayerInfo(new PlayerInfo(ZebraEngine.PLAYER_WHITE,  settingsLoader.mSettingZebraDepth,  settingsLoader.mSettingZebraDepthExact,  settingsLoader.mSettingZebraDepthWLD, ZebraEngine.INFINIT_TIME, 0));
					break;
			}
			mZebraThread.setPlayerInfo(new PlayerInfo(ZebraEngine.PLAYER_ZEBRA,  settingsLoader.mSettingZebraDepth + 1,  settingsLoader.mSettingZebraDepthExact + 1,  settingsLoader.mSettingZebraDepthWLD + 1, ZebraEngine.INFINIT_TIME, 0));

			switch (settingsLoader.mSettingZebraRandomness) {
				case RANDOMNESS_SMALL:
					mZebraThread.setSlack(1);
					mZebraThread.setPerturbation(1);
					break;
				case RANDOMNESS_MEDIUM:
					mZebraThread.setSlack(4);
					mZebraThread.setPerturbation(2);
					break;
				case RANDOMNESS_LARGE:
					mZebraThread.setSlack(6);
					mZebraThread.setPerturbation(6);
					break;
				case RANDOMNESS_HUGE:
					mZebraThread.setSlack(10);
					mZebraThread.setPerturbation(16);
					break;
				case RANDOMNESS_NONE:
				default:
					mZebraThread.setSlack(0);
					mZebraThread.setPerturbation(0);
					break;
			}
		} catch (EngineError e) {
			showAlertDialog(e.getError());
		}

		mStatusView.setTextForID(
				StatusView.ID_SCORE_SKILL,
				String.format(getString(R.string.display_depth),  settingsLoader.mSettingZebraDepth,  settingsLoader.mSettingZebraDepthExact,  settingsLoader.mSettingZebraDepthWLD)
		);


		if( !settingsLoader.mSettingDisplayPV ) {
			mStatusView.setTextForID(StatusView.ID_STATUS_PV, "");
			mStatusView.setTextForID(StatusView.ID_STATUS_EVAL, "");
		}

		mZebraThread.setMoveDelay (settingsLoader.mSettingDisplayEnableAnimations?  settingsLoader.mSettingAnimationDelay + 1000 : 0);

		if( bZebraSettingChanged ) {
			mZebraThread.sendSettingsChanged();
		}
	}

	private void sendMail(){
		//GetNowTime
		Calendar calendar = Calendar.getInstance();
		Date nowTime = calendar.getTime();
		StringBuilder sbBlackPlayer = new StringBuilder();
		StringBuilder sbWhitePlayer = new StringBuilder();
		ZebraBoard gs = mZebraThread.getGameState();
		SharedPreferences settings = getSharedPreferences(SHARED_PREFS_NAME, 0);
		byte[] moves = null;
		if( gs != null ) {
			moves = gs.getMoveSequence();
		}

		Intent intent = new Intent();
		Intent chooser = Intent.createChooser(intent, "");

		intent.setAction(Intent.ACTION_SEND);
		intent.setType("message/rfc822");
		intent.putExtra(
				Intent.EXTRA_EMAIL,
				new String[]{settings.getString(SETTINGS_KEY_SENDMAIL, DEFAULT_SETTING_SENDMAIL)});

		intent.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.app_name));

		//get BlackPlayer and WhitePlayer
		switch( settingsLoader.mSettingFunction ) {
			case FUNCTION_HUMAN_VS_HUMAN:
				sbBlackPlayer.append("Player");
				sbWhitePlayer.append("Player");
				break;
			case FUNCTION_ZEBRA_BLACK:
				sbBlackPlayer.append("DroidZebra-");
				sbBlackPlayer.append (settingsLoader.mSettingZebraDepth);
				sbBlackPlayer.append("/");
				sbBlackPlayer.append (settingsLoader.mSettingZebraDepthExact);
				sbBlackPlayer.append("/");
				sbBlackPlayer.append (settingsLoader.mSettingZebraDepthWLD );

				sbWhitePlayer.append("Player");
				break;
			case FUNCTION_ZEBRA_WHITE:
				sbBlackPlayer.append("Player");

				sbWhitePlayer.append("DroidZebra-");
				sbWhitePlayer.append (settingsLoader.mSettingZebraDepth);
				sbWhitePlayer.append("/");
				sbWhitePlayer.append (settingsLoader.mSettingZebraDepthExact);
				sbWhitePlayer.append("/");
				sbWhitePlayer.append (settingsLoader.mSettingZebraDepthWLD );
				break;
			case FUNCTION_ZEBRA_VS_ZEBRA:
				sbBlackPlayer.append("DroidZebra-");
				sbBlackPlayer.append (settingsLoader.mSettingZebraDepth);
				sbBlackPlayer.append("/");
				sbBlackPlayer.append (settingsLoader.mSettingZebraDepthExact);
				sbBlackPlayer.append("/");
				sbBlackPlayer.append (settingsLoader.mSettingZebraDepthWLD );

				sbWhitePlayer.append("DroidZebra-");
				sbWhitePlayer.append (settingsLoader.mSettingZebraDepth);
				sbWhitePlayer.append("/");
				sbWhitePlayer.append (settingsLoader.mSettingZebraDepthExact);
				sbWhitePlayer.append("/");
				sbWhitePlayer.append (settingsLoader.mSettingZebraDepthWLD );
			default:
		}
		StringBuilder sb = new StringBuilder();
		sb.append(getResources().getString(R.string.mail_generated));
		sb.append("\r\n");
		sb.append(getResources().getString(R.string.mail_date));
		sb.append(" ");
		sb.append(nowTime);
		sb.append("\r\n\r\n");
		sb.append(getResources().getString(R.string.mail_move));
		sb.append(" ");
		StringBuilder sbMoves = new StringBuilder();
		if(moves != null){

            for (byte move1 : moves) {
                if (move1 != 0x00) {
                    Move move = new Move(move1);
					sbMoves.append(move.getText());
					if (Objects.equal(state.getmLastMove(), move)) {
						break;
					}
				}
			}
		}
		sb.append(sbMoves);
		sb.append("\r\n\r\n");
		sb.append(sbBlackPlayer.toString());
		sb.append("  (B)  ");
		sb.append(state.getmBlackScore());
		sb.append(":");
		sb.append(state.getmWhiteScore());
		sb.append("  (W)  ");
		sb.append(sbWhitePlayer.toString());

		intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
		// Intent
		// Verify the original intent will resolve to at least one activity
		if (intent.resolveActivity(getPackageManager()) != null) {
			startActivity(chooser);
		}
	}

	private void switchSides() {
		int newFunction = -1;

		if(  settingsLoader.mSettingFunction == FUNCTION_ZEBRA_WHITE )
			newFunction = FUNCTION_ZEBRA_BLACK;
		else if(  settingsLoader.mSettingFunction == FUNCTION_ZEBRA_BLACK )
			newFunction = FUNCTION_ZEBRA_WHITE;

		if(newFunction>0) {
			SharedPreferences settings = getSharedPreferences(SHARED_PREFS_NAME, 0);
			SharedPreferences.Editor editor = settings.edit();
            editor.putString(SETTINGS_KEY_FUNCTION, String.format(Locale.getDefault(), "%d", newFunction));
			editor.apply();
		}

		loadSettings();

		// start a new game if not playing
		if( !mZebraThread.gameInProgress() )
			newGame();
	}

	private void showHint() {
		if(  !settingsLoader.mSettingZebraPracticeMode ) {
			mHintIsUp = true;
			mZebraThread.setPracticeMode(true);
			mZebraThread.sendSettingsChanged();
		}
	}

	@Override
	protected void onDestroy() {
		boolean retry = true;
		mZebraThread.setRunning(false);
		mZebraThread.interrupt(); // if waiting
		while (retry) {
			try {
				mZebraThread.join();
				retry = false;
			} catch (InterruptedException e) {
                Log.wtf("wtf", e);
			}
		}
		mZebraThread.clean();
		super.onDestroy();
	}

	void showDialog(DialogFragment dialog, String tag) {
		if( mActivityActive ) {
			runOnUiThread(() -> dialog.show(getSupportFragmentManager(), tag));
		}
	}

	public void showPassDialog() {
		DialogFragment newFragment = DialogPass.newInstance();
		showDialog(newFragment, "dialog_pass");
	}

    @Override
    public boolean getSettingDisplayPV() {
        return  settingsLoader.mSettingDisplayPV;
    }

    public void showGameOverDialog() {
		DialogFragment newFragment = DialogGameOver.newInstance();
		showDialog(newFragment, "dialog_gameover");
	}

	public void showQuitDialog() {
		DialogFragment newFragment = DialogQuit.newInstance();
		showDialog(newFragment, "dialog_quit");
	}

	private void enterMoves() {
		DialogFragment newFragment = EnterMovesDialog.newInstance(clipboard);
		showDialog(newFragment, "dialog_moves");
	}

	public void setUpBoard(String s) {
		final LinkedList<Move> moves = parser.makeMoveList(s);
		mZebraThread.sendReplayMoves(moves);
	}

	public void showBusyDialog() {
		if (!mBusyDialogUp && mZebraThread.isThinking()) {
			DialogFragment newFragment = DialogBusy.newInstance();
			mBusyDialogUp = true;
			showDialog(newFragment, "dialog_busy");
		}
	}

	public void dismissBusyDialog() {
		if (mBusyDialogUp) {
			Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog_busy");
			if (prev != null) {
				DialogFragment df = (DialogFragment) prev;
				df.dismiss();
			}
			mBusyDialogUp = false;
		}
	}

    @Override
    public boolean isHintUp() {
        return mHintIsUp;
    }

    @Override
    public void setHintUp(boolean value) {
        mHintIsUp = true;
    }

    @Override
    public boolean isPraticeMode() {
        return  settingsLoader.mSettingZebraPracticeMode;
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		if (mZebraThread != null)
			loadSettings();
	}

    public void showAlertDialog(String msg) {
        DroidZebra.this.newGame();
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
		alertDialog.setTitle("Zebra Error");
		alertDialog.setMessage(msg);
        alertDialog.setPositiveButton("OK", (dialog, id) -> alert = null);
		runOnUiThread(() -> alert = new WeakReference<>(alertDialog.show()));
    }

    @Override
    public StatusView getStatusView() {
        return mStatusView;
    }

    @Override
    public BoardView getBoardView() {
        return mBoardView;
    }

    public WeakReference<AlertDialog> getAlert() {
        return alert;
	}

	@Override
	protected void onPause() {
		super.onPause();
		mActivityActive = false;
	}

	@Override
	protected void onResume() {
		super.onResume();
		mActivityActive = true;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			mZebraThread.undoMove();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		ZebraBoard gs = mZebraThread.getGameState();
		if (gs != null) {
			outState.putByteArray("moves_played", gs.getMoveSequence());
			outState.putInt("moves_played_count", gs.getDisksPlayed());
			outState.putInt("version", 1);
		}
	}

	public BoardState getState() {
		return state;
	}


	//-------------------------------------------------------------------------
	// Pass Dialog
	public static class DialogPass extends DialogFragment {

		public static DialogPass newInstance() {
	    	return new DialogPass();
	    }

		public DroidZebra getDroidZebra() {
			return (DroidZebra)getActivity();
		}

		@Override
        @NonNull
		public Dialog onCreateDialog(Bundle savedInstanceState) {
	    	return new AlertDialog.Builder(getActivity())
			.setTitle(R.string.app_name)
			.setMessage(R.string.dialog_pass_text)
					.setPositiveButton(R.string.dialog_ok, (dialog, id) -> getDroidZebra().mZebraThread.setEngineState(ZebraEngine.ES_PLAY)
			)
			.create();
	    }
	}

	//-------------------------------------------------------------------------
	// Game Over Dialog
	public static class DialogGameOver extends DialogFragment {

		public static DialogGameOver newInstance() {
	    	return new DialogGameOver();
	    }

		public DroidZebra getDroidZebra() {
			return (DroidZebra)getActivity();
		}

		public void refreshContent(View dialog) {
			int winner;
			int blackScore = getDroidZebra().state.getmBlackScore();
			int whiteScore = getDroidZebra().state.getmWhiteScore();
			if (whiteScore > blackScore)
				winner =R.string.gameover_text_white_wins;
			else if (whiteScore < blackScore)
				winner = R.string.gameover_text_black_wins;
			else
				winner = R.string.gameover_text_draw;

			((TextView)dialog.findViewById(R.id.gameover_text)).setText(winner);

            ((TextView) dialog.findViewById(R.id.gameover_score)).setText(String.format(Locale.getDefault(), "%d : %d", blackScore, whiteScore));
		}

		@Override
	   public View onCreateView(LayoutInflater inflater, ViewGroup container,
	            Bundle savedInstanceState) {

			getDialog().setTitle(R.string.gameover_title);

			View v = inflater.inflate(R.layout.gameover, container, false);

			Button button;
			button = (Button) v.findViewById(R.id.gameover_choice_new_game);
			button.setOnClickListener(
					v15 -> {
						dismiss();
						getDroidZebra().newGame();
					});

			button = (Button) v.findViewById(R.id.gameover_choice_switch);
			button.setOnClickListener(
					v12 -> {
						dismiss();
						getDroidZebra().switchSides();
					});

			button = (Button) v.findViewById(R.id.gameover_choice_cancel);
			button.setOnClickListener(
					v1 -> dismiss());

			button = (Button) v.findViewById(R.id.gameover_choice_options);
			button.setOnClickListener(
					v13 -> {
						dismiss();

						// start settings
						Intent i = new Intent(getDroidZebra(), SettingsPreferences.class);
						startActivity(i);
					});

			button = (Button) v.findViewById(R.id.gameover_choice_email);
			button.setOnClickListener(
					v14 -> {
						dismiss();
						getDroidZebra().sendMail();
					});

			refreshContent(v);

			return v;
		}

		@Override
	   public void onResume() {
	     super.onResume();
	     refreshContent(getView());
	   }
	}

	//-------------------------------------------------------------------------
	// Pass Dialog
	public static class DialogQuit extends DialogFragment {

		public static DialogQuit newInstance() {
	    	return new DialogQuit();
	    }

		public DroidZebra getDroidZebra() {
			return (DroidZebra)getActivity();
		}

		@NonNull
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			return new AlertDialog.Builder(getActivity())
					.setTitle(R.string.dialog_quit_title)
					.setPositiveButton(R.string.dialog_quit_button_quit, (dialog, id) -> getDroidZebra().finish()
					)
					.setNegativeButton( R.string.dialog_quit_button_cancel, null )
					.create();
		}
	}

	public Gameparser getParser() {
		return parser;
	}

	//-------------------------------------------------------------------------
	// Pass Dialog
	public static class DialogBusy extends DialogFragment {

		public static DialogBusy newInstance() {
	    	return new DialogBusy();
	    }

		public DroidZebra getDroidZebra() {
			return (DroidZebra)getActivity();
		}

		@NonNull
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			ProgressDialog pd = new ProgressDialog(getActivity()) {
				@Override
				public boolean onKeyDown(int keyCode, KeyEvent event) {
					if( getDroidZebra().mZebraThread.isThinking() ) {
						getDroidZebra().mZebraThread.stopMove();
					}
					getDroidZebra().mBusyDialogUp = false;
					cancel();
					return super.onKeyDown(keyCode, event);
				}

				@Override
				public boolean onTouchEvent(MotionEvent event) {
					if(event.getAction()==MotionEvent.ACTION_DOWN) {
						if( getDroidZebra().mZebraThread.isThinking() ) {
							getDroidZebra().mZebraThread.stopMove();
						}
						getDroidZebra().mBusyDialogUp = false;
						cancel();
						return true;
					}
					return super.onTouchEvent(event);
				}
			};
			pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			pd.setMessage(getResources().getString(R.string.dialog_busy_message));
			return pd;
		}
	}
}
