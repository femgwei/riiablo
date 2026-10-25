package com.riiablo.engine.server.ai;

import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.State;

/** Same state machine during play; restoration never calls enter/exit/changeState. */
final class CheckpointStateMachine<S extends State<Integer>> extends DefaultStateMachine<Integer, S> {
  CheckpointStateMachine(int owner, S initial) { super(owner, initial); }
  void restore(S current, S previous, S global) {
    currentState = current; previousState = previous; globalState = global;
  }
}
