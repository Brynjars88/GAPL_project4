package GAPL_project3;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.ggp.base.util.game.Game;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

import javafx.util.Pair;


public class utils {

	/**
	 * Takes an absolute path to a GDL file and returns a StateMachine,
     * intialized with the game description from gdlFile
	 * @param gdlFile - Absolute path to a GDL file
	 * @return initalized StateMachine
	 * @throws IOException
	 */
	public static StateMachine getGameSM(String gdlFile) throws IOException{
		// Takes in absolute path of a GDL file and returns a new state machine with that description

		FileInputStream fstream = new FileInputStream(gdlFile);
		BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
		String strLine = br.readLine();
		StringBuilder sb = new StringBuilder();

		while (strLine != null) {
			if(!strLine.startsWith(";")) {
				sb.append(strLine).append("\n");
			}
			strLine = br.readLine();
		}
		br.close();
		String gd = sb.toString();
		gd = gd.replaceAll("[\n]+","\n");

		gd = Game.preprocessRulesheet(gd);
		Game g = Game.createEphemeralGame(gd);
		List<Gdl> rules = g.getRules();
		StateMachine s = new ProverStateMachine();
		s.initialize(rules);

		return s;
	}

	/**
	 * Returns the index of the next Move action (node to visit) to take/examine
	 * for a given role based on the current node's Q values for its children and
	 * the number of times a child node has been visited. The move-index chosen will
	 * be the one that maximizes the moves UTC value. The arrays moves[], Qs[] and Ns[]
	 * must all be of the same size.
	 * @param moves - all legal moves from the current node's state for a
	 * given role
	 * @param Qs - Q values for all roles' moves from the current state
	 * @param Ns - number of times a role has chosen a specific move
	 * @param N - number of times current state has been visited
	 * @param C - controls exploration (higher C => values less explored are
	 * prioritised)  vs. exploitation (lower C => values with better Q score
	 * are prioritised). Typical values for C are in the interval [0,100]
	 * @return next Move-index for a given role.
	 */
	public static int UCT(Move[] moves, double[] Qs, int[] Ns, int N, double C) {
		double max = Double.MIN_VALUE;
		int returnIndex = 0;
		double val;
		for(int i = 0; i < moves.length; i++) {
			if (Ns[i] == 0 && C != 0) return i;
			val = Qs[i] + C*Math.sqrt(Math.log((double) N)/((double) Ns[i]));
			if (val > max) {
				max = val;
				returnIndex = i;
			}
		}
		return returnIndex;
	}

	/**
	 * Simulates a random game from the state of node/GameTree t.
	 * @param t - Current node/GameTree
	 * @param machine - Initalized StateMachine with the GDL rules of the game
	 * @return double array with GoalValues from that random game for all roles
	 * in the order machine fetches them
	 * @throws GoalDefinitionException
	 * @throws MoveDefinitionException
	 * @throws TransitionDefinitionException
	 */
	public static double[] rollout(GameTree t, StateMachine machine) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		MachineState ms = t.getState();
		if (machine.isTerminal(ms)) { // Base case
			List<Role> roles = machine.getRoles();
			double[] goalValues = new double[roles.size()];
			for(int i = 0; i < roles.size(); i++) {
				goalValues[i] = (double) machine.getGoal(ms, roles.get(i));
			}
			return goalValues;
		}else{
			Move[] randMove = getRandJointMoves(t.getLegalMoves()); // Are we SURE the random jointMove created appear in the same order
																	// that they would be stored in the GameTree t?
			GameTree randChild = t.getChild(Arrays.asList(randMove));
			return rollout(randChild, machine);
		}
	}

	/**
	 * After rollout/simulation from a chosen node/GameTree/state all ancestor
	 * nodes' Q values for the chosen node to simulate a random game from must
	 * be updated
	 * @param t - GameTree/node from where the rollout took place
	 * @param machine - initalized StateMachine with the relevant game description
	 * @param goalVal - array of double goal values found from the random
	 * simulation; values must be in the same order as the StateMachine would
	 * fetch the roles
	 * @param moveList - Stack (as an ArrayList) of move indices taken for every
	 * role that were taken to reach the current state. Required to update correct
	 * indices in the Q arrays of each node. For instance if there were 3 roles,
	 * moveList could look like this:
	 * [4,1,0] -> [2,0,2] -> [0,2,1]
	 * The first int array represents the moves (as indices) each role took in
	 * the oldest ancestor node. The last int array are the most recent moves
	 * taken. This assumes the order of roles is the same as the StateMachine
	 * would fetch them in
	 */
	public static void backPropagate(GameTree t, StateMachine machine, double[] goalVal, ArrayList<int[]> moveList) {
		int[] theMoves = moveList.remove(moveList.size()-1);
		for(int i = 0; i < theMoves.length; i++) {
			t.updateQScore(i, theMoves[i], goalVal[i]);
			t.incrNs(i, theMoves[i]);
		}
		if (t.getParent() != null) { // Base case is parent == null, in that case we don't do anything else
			backPropagate(t.getParent(), machine, goalVal, moveList);
		}
	}

	/**
	 * Creates an array of randomly chosen Move actions for every role, in
	 * conjunction these represent a joint Move.
	 * @param legalMoves - Move[][] array, rows represent legal moves for one
	 * role
	 * @return Array of Move objects for every role. This assumes roles are
	 * indexed in the same order as a StateMachine would fetch them.
	 */
	public static Move[] getRandJointMoves(Move[][] legalMoves) {
		Move[] jointMove = new Move[legalMoves.length];
		int noLegalMoves;
		int randint;
		for(int i = 0; i < legalMoves.length; i++) {
			noLegalMoves = legalMoves[i].length;
			randint = (new Random()).nextInt(noLegalMoves);
			jointMove[i] = legalMoves[i][randint];
		}
		return jointMove;
	}


	/**
	 *
	 * @param node
	 * @param machine
	 * @param timeLimit
	 * @param maxIter
	 * @param C
	 * @return
	 * @throws MoveDefinitionException
	 */
	public static Pair<Move,GameTree> MCTS(GameTree node, StateMachine machine, int timeLimit, int maxIter, double C) throws MoveDefinitionException {

		int iter = 0;

		while(iter <= maxIter) {

			int nRoles = node.getNoRoles();

			/* PHASE 1 - SELECTION */
			Move[] jointMove = new Move[nRoles];
			int[] jmIndex = new int[nRoles];
			for (int i = 0; i < nRoles; i++) {
				jmIndex[i] = UCT(node.getLegalMoves()[i], node.getAllQScores()[i], node.getAllNs()[i], node.getNoSimulation(), C);
			}

			/* PHASE 2 - EXPANSION */


			/* PHASE 3 - PLAYOUT */

			/* PHASE 4 - BACK-PROPAGATION */
		}
		return new Pair<Move,GameTree>(new Move(null),new GameTree(null,null,null));
	}
}
