public class TeamAM extends Player {

	// for DoubleAgent
	private Handshaker shaker;

	boolean opponentHadWinningPosition; //set to true if opponent can force a win at any point in the match.
	int trust;
	// TODO BY ZOE: REMEMBER TO TURN THIS OFF
	public final boolean DEBUG_MODE = true;

	public byte[] bestMoveBytesRealist, scoreWhiteBytesRealist, scoreBlackBytesRealist;
	public byte[] bestMoveBytesCooperative, scoreWhiteBytesCooperative, scoreBlackBytesCooperative;

	public int BETRAYAL_DELTA = 1;
	public int COOPERATION_DELTA = 1;
	public int IRRATIONALITY_DELTA = 2;
	public int SUBOPTIMALITY_DELTA = 1;

	int monkeyScore;
	boolean isDetectMonkeyModeOn;

	boolean isOpponentMonkey;
	boolean isOpponentNihilist = false;
	boolean isOpponentOptimist = false;
	boolean isOpponentPessimist = false;
	boolean isOpponentQueller = false;
	boolean isOpponentRealist = false;
	boolean isOpponentScrapper = false;
	boolean isOpponentTruster = false;
	boolean isOpponentUtilitarian = false;

	boolean opponentCanCaptureKingThisRound;
	boolean opponentCanCaptureRookThisRound;

	int matchNum;

	public TeamAM(int maxNumMoves) {
		TeamRational teamRationalRealist = TeamRational.createRealist(maxNumMoves);
		// Take the data that we need from teamRationalRealist:
		bestMoveBytesRealist = teamRationalRealist.bestMoveBytes;
		scoreWhiteBytesRealist = teamRationalRealist.scoreWhiteBytes;
		scoreBlackBytesRealist = teamRationalRealist.scoreBlackBytes;
		// then teamRationalRealist will be deconstructed.

		TeamRational teamRationalCooperative = TeamRational.createCooperative(maxNumMoves);
		// Take the data that we need from teamRationalCooperative:
		bestMoveBytesCooperative = teamRationalCooperative.bestMoveBytes;
		scoreWhiteBytesCooperative = teamRationalCooperative.scoreWhiteBytes;
		scoreBlackBytesCooperative = teamRationalCooperative.scoreBlackBytes;
		// then teamRationalCooperative will be deconstructed.
		shaker = Handshaker.createHandshakeAccepter();
	}

	public void prepareForSeries() {
		trust = 1;
		monkeyScore = 0;
		matchNum = 0;
		isDetectMonkeyModeOn = true;
		isOpponentMonkey = false;
		opponentCanCaptureKingThisRound = false;
		opponentCanCaptureRookThisRound = false;
		shaker.handshakePrepareForSeries();
	}

	public String parseThreatenBy(String input) {
		try{
			return input.substring(input.lastIndexOf('/') + 1);
		} catch(Exception e) {
			if(DEBUG_MODE) {
				System.out.println("THERE IS A FUCKING STRING ERROR!!!!");
			}
			// let's just swing it
			return "EXCEPTION";
		}
	}

	public void prepareForMatch() {
		BoardPosition boardPosition;

		opponentHadWinningPosition = false;

		matchNum += 1;

		// Start in second : record the gameboard config?
		if (myColour == BLACK) {
			System.out.println("\n**************[prepareForMatch] Match "+matchNum+" We are BLACK");
			boardPosition = toBoardPosition();
			if (scoreBlackBytesRealist[boardPosition.toInt()] == 0) {
				opponentHadWinningPosition = true;
			}

			String kingRes = checkNextThreaten(myKingRow, myKingColumn, "king", "InsidePrepareForMatch");
			String rookRes = checkNextThreaten(myRookRow, myRookColumn, "rook", "InsidePrepareForMatch");

			String myKingThreatenBy = parseThreatenBy(kingRes);
			String myRookThreatenBy = parseThreatenBy(rookRes);


			opponentCanCaptureKingThisRound = !myKingThreatenBy.equals("safe");
			opponentCanCaptureRookThisRound = !myRookThreatenBy.equals("safe");

			if(DEBUG_MODE) {
				System.out.println("===");
				System.out.println("[prepareForMatch] isMyTurn: " + (myColour == numMovesPlayed%2));
				System.out.println("[prepareForMatch] kingRes: "+kingRes);
				System.out.println("[prepareForMatch] rookRes: "+rookRes);

				if(opponentCanCaptureRookThisRound) System.out.println("[prepareForMatch] Opponent can capture our rook ");
				if(opponentCanCaptureKingThisRound) System.out.println("[prepareForMatch] Opponent can capture our king");
				System.out.println("===");
			}

		} else {
			 if(DEBUG_MODE) System.out.println("\n**************[prepareForMatch] Match "+matchNum+" We are WHITE");

		}
		shaker.handshakePrepareForMatch(toBoardPosition());
	}

	public void receiveMatchOutcome(int matchOutcome) {
		//Convert to a more reasonable format first:
		int myMatchPayoff = outcomeToPayoff(matchOutcome);
		trust = updateTrust(trust, myMatchPayoff);
		//System.out.println("Match "+matchNum+" This is a monkey: "+isOpponentMonkey+" Monkey score is "+monkeyScore);
		shaker.handshakeReceiveMatchOutcome(matchOutcome, toBoardPosition());
	}

	public int updateTrust(int trust, int myMatchPayoff) {
		if (trust>0) { // I trusted you! Let's see how you did:
			if (myMatchPayoff < 2) {
				// I didn't take your king? I trust you less now.
				return trust - BETRAYAL_DELTA;
			} else if (myMatchPayoff == 3) {
				// I tried to tie, but I won!!! I don't trust that you know what you're doing.
				//isOpponentMonkey += 2;
				return trust - IRRATIONALITY_DELTA;
			}
		} else if (opponentHadWinningPosition && myMatchPayoff == 2) {
			// I didn't trust you. I'm very picky about restoring trust!
			// You gave up a win for a tie? I trust you more now.
			return trust + COOPERATION_DELTA;

		} else if (opponentHadWinningPosition && myMatchPayoff != 2) {
			// I don't believe that you needed to let me win.
			//isOpponentMonkey += 4;
			return trust - SUBOPTIMALITY_DELTA;
		}
		return trust;
	}


	public boolean isBlocked(int src, int dest, int curr) {
		return (src <= curr && curr <= dest) ||
			   (dest <= curr && curr <= src);
	}

	// can be used to check current threaten as well
	public String checkNextThreaten(int myNextRow, int myNextCol, String myNextPieceType, String state) {
		// those two statements shouldn't be printed at any cases!
		if (myNextPieceType.equals("king") && !myKingIsAlive) return "delete non-existing king /error";
		if (myNextPieceType.equals("rook") && !myRookIsAlive) return "delete non-existing rook /error";

		boolean doesMoveCaptureTheirKing = myNextRow==theirKingRow && myNextCol==theirKingColumn;
		boolean doesMoveCaptureTheirRook = myNextRow==theirRookRow && myNextCol==theirRookColumn;

		boolean isThreatenByTheirKing = (!doesMoveCaptureTheirKing && theirKingIsAlive && Math.abs(theirKingRow - myNextRow) <= 1 && Math.abs(theirKingColumn - myNextCol) <= 1);

		// isThreatenByTheirRook: two directions
		boolean threatenHorizon;
		boolean threatenVertical;
	//	if(myColour != 1 && numMovesPlayed != 0) {
												// same row
			threatenHorizon = (theirRookRow == myNextRow) &&
												// blocked if their king is on the same row and blocks the column
												!(isBlocked(theirRookColumn, myNextCol, theirKingColumn)&&theirKingRow==theirRookRow) &&
												// blocked if their rook is on the same row and blocks the column
												!(isBlocked(theirRookColumn, myNextCol, myRookColumn)&&myRookRow==theirRookRow);
												// same column
			threatenVertical = (theirRookColumn == myNextCol) &&
												// blocked if their king is on the same column and blocks the row
												!(isBlocked(theirRookRow, myNextRow, theirKingRow) &&  theirKingColumn == theirRookColumn) &&
												!(isBlocked(theirRookRow, myNextRow, myRookRow) && myRookColumn == theirRookColumn);
/*
		} else {
			threatenHorizon = (theirRookRow == myNextRow) && !isBlocked(theirRookColumn, myNextCol, theirKingColumn);
			threatenVertical = (theirRookColumn == myNextCol) && !isBlocked(theirRookRow, myNextRow, theirKingRow);
		}
*/
		boolean isThreatenByTheirRook = (!doesMoveCaptureTheirRook && theirRookIsAlive && (threatenHorizon || threatenVertical));

		String res = myNextPieceType;

		if(isThreatenByTheirKing && isThreatenByTheirRook) res += " threaten by /both";
		else if(isThreatenByTheirKing) res += " threaten by /theirKing";
		else if(isThreatenByTheirRook) res += " threaten by /theirRook";
		else
			res = "/safe";

		if(DEBUG_MODE) {
			System.out.println("STATE = XXXX "+state);
			System.out.println("Current numMovesPlayed "+ numMovesPlayed);
			System.out.println("My next "+myNextPieceType+": [" + myNextRow + ", " + myNextCol + "]");
			System.out.println("My current king: ["+myKingRow+", "+myKingColumn+"]");
			System.out.println("My current rook: ["+myRookRow+", "+myRookColumn+"]");
			System.out.println("Their current king: ["+theirKingRow+", "+theirKingColumn+"]");
			System.out.println("Their current rook: ["+theirRookRow+", "+theirRookColumn+"]");
		}

		return res;
	}

	// Against DoubleAgent ONLY!!!
	public MoveDescription chooseMove() {
		BoardPosition currentBoardPosition = toBoardPosition();
		// update shaker with opponent move
		shaker.updateTheirMove(currentBoardPosition);

		MoveDescription myMove;
		if (shaker.shouldSendHandshakeMove()) {
			myMove=Handshaker.getHandshakeMove(currentBoardPosition);
		} else {
			myMove = internalChooseMove();
		}

		shaker.receiveMyMove(myMove);
		return myMove;
	}

	// the original chooseMove: now this is for all the non-doubleAgent player
	public MoveDescription internalChooseMove() {
		BoardPosition boardPosition = toBoardPosition();
		int currentPlayerColour = (boardPosition.numMovesPlayed % 2 == 0) ? WHITE : BLACK;
		opponentHadWinningPosition = updateOpponentHadWinningPosition(boardPosition, currentPlayerColour);
		return bestMoveFromTrust(boardPosition, currentPlayerColour);
	}

	// check if king/rook will be eaten in the next round
	public boolean kingOrRookCanBeCapturedNextRound(MoveDescription nextMove) {
		int myNextRow = nextMove.getDestinationRow();
		int myNextCol = nextMove.getDestinationColumn();
		String piece = nextMove.getPieceToMove();

		return (checkNextThreaten(myNextRow, myNextCol, piece, "CHECKBOTH").equals("safe"));
	}

	// core function to detect monkey
	public boolean isAgainstMonkey(int bestScoreCooperative, int bestScoreRealist) {

		// boolean isMyTurn = (numMovesPlayed % 2 == 0 && myColour == 0) || (numMovesPlayed%2!=0 && myColour == 1);
		boolean isMyTurn = (numMovesPlayed%2 == myColour);

		/*
		什么情况一定不是猴子

		如果是上一轮 对方走 并且对方可以吃KING
		X+1的时候 KING还在 那么不是

		如果是上一轮 对方走 并且对方只能吃ROOK
		X+1的时候 ROOK还在 那么不是
		*/

		if(isDetectMonkeyModeOn)
		{
			if(DEBUG_MODE) {
				System.out.println("[CORE] numMovesPlayed: "+numMovesPlayed);
				System.out.println("[CORE] myColour:"+myColour);
				System.out.println("[CORE] isMyTurn: "+isMyTurn);
				System.out.println("Can opponent capture our king? "+opponentCanCaptureKingThisRound);
				System.out.println("Can opponent capture our Rook? "+opponentCanCaptureRookThisRound);
				System.out.println("My current king: ["+myKingRow+", "+myKingColumn+"]");
				System.out.println("My current rook: ["+myRookRow+", "+myRookColumn+"]");
				System.out.println("Their current king: ["+theirKingRow+", "+theirKingColumn+"]");
				System.out.println("Their current rook: ["+theirRookRow+", "+theirRookColumn+"]");
			}

			if (opponentCanCaptureKingThisRound && numMovesPlayed != 0)
			{
				if (!myKingIsAlive)
				{	 // our king was captured in the last round!
					if (trust < 0 && (bestScoreCooperative == 3 || bestScoreRealist == 2))
					{

						if(DEBUG_MODE) System.out.println("Oppo took my king while we can tie! \nIn Match "+matchNum+" Monkey score added! because trust is "+trust);

						monkeyScore += 4;
					}
				}

				else {

					if(DEBUG_MODE) System.out.println("Oppo could take my king but he did not. Definitely not a monkey at "+numMovesPlayed);

					isOpponentMonkey = false;
					isDetectMonkeyModeOn = false;
				}
			}

			else if (opponentCanCaptureRookThisRound && numMovesPlayed != 0)
			{
				if (!myRookIsAlive)
				{
					if (trust < 1)
					{
						if(DEBUG_MODE) System.out.println("Oppo took my rook! \nIn Match "+matchNum+" Monkey score added! because trust is "+trust);
						monkeyScore += 4;
					}
				}
				else
				{
					if(DEBUG_MODE) {
						System.out.println("Oppo could take my rook but he did not. Definitely not a monkey at "+numMovesPlayed);
						System.out.println("Is our rook alive? "+myRookIsAlive);
						System.out.println("Our rook row "+myRookRow+" Our rook col "+myRookColumn);
						System.out.println("Can opponent capture our rook? "+opponentCanCaptureRookThisRound);
						System.out.println("Our king row "+myKingRow+" Our rook col "+myKingColumn);
						System.out.println("Their rook row "+theirRookRow+" their rook col "+theirRookColumn);
						System.out.println("Their king row "+theirKingRow+" Their king col "+theirKingColumn);
					}

					isOpponentMonkey = false;
					isDetectMonkeyModeOn = false;
				}
			}

			if(monkeyScore >= 20) {
				isOpponentMonkey = true;
				isDetectMonkeyModeOn = false;
				if(DEBUG_MODE) System.out.println("Fuck god, you are a monkey");
			}
		}
		return isOpponentMonkey;
	}

	public MoveDescription bestMoveFromTrust(BoardPosition boardPosition, int currentPlayerColour) {

		TeamRational.Node nodeRealist = new TeamRational.Node(
				bestMoveBytesRealist[boardPosition.toInt()],
				scoreWhiteBytesRealist[boardPosition.toInt()],
				scoreBlackBytesRealist[boardPosition.toInt()]);

		int bestScoreRealist = nodeRealist.getScore(currentPlayerColour);

		TeamRational.Node nodeCooperative = new TeamRational.Node(
				bestMoveBytesCooperative[boardPosition.toInt()],
				scoreWhiteBytesCooperative[boardPosition.toInt()],
				scoreBlackBytesCooperative[boardPosition.toInt()]);

		int bestScoreCooperative = nodeCooperative.getScore(currentPlayerColour);

		isOpponentMonkey = isAgainstMonkey(bestScoreCooperative, bestScoreRealist);

		// TODO: change the logic for this one
		if(isOpponentMonkey) {
			System.out.println("Activating Monkey Code...");
			int iteration = 20;
			while(iteration>0) {
				MoveDescription move = nodeRealist.bestMove;
				if(!kingOrRookCanBeCapturedNextRound(move)){
					return move;
				}
				iteration -= 1;
			}
		}

		MoveDescription move;
		//if (bestScoreRealist==2 || (isMonkey == true && matchNum <= 12)) {
		if (bestScoreRealist==2) {
			//System.out.println("Monkey score is "+isOpponentMonkey);
			// If the move forces a tie, play it in all cases (to be safe):
			move = nodeRealist.bestMove;
		} else if (trust > 0 && bestScoreCooperative == 3) {
			// If trust remains, play cooperatively for a tie:
			move = nodeCooperative.bestMove;
		} else {
			// In all other cases, play realistically:
			move = nodeRealist.bestMove;
		}


		int myNextRow = move.getDestinationRow();
		int myNextCol = move.getDestinationColumn();
		String myNextPieceType = move.getPieceToMove();

		checkNextThreaten(myNextRow, myNextCol, myNextPieceType, "BESTMOVE");

		return move;
	}

	public boolean updateOpponentHadWinningPosition(BoardPosition boardPosition, int currentPlayerColour) {
		TeamRational.Node nodeRealist = new TeamRational.Node(
				bestMoveBytesRealist[boardPosition.toInt()],
				scoreWhiteBytesRealist[boardPosition.toInt()],
				scoreBlackBytesRealist[boardPosition.toInt()]);

		int bestScoreRealist = nodeRealist.getScore(currentPlayerColour);

		if (opponentHadWinningPosition || bestScoreRealist == 0) {
			return true;
		}

		return false;
	}
}
