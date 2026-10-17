package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;

/** Hold-to-run input state which does not change the persistent run/walk preference. */
@PooledWeaver
public class TemporaryRunning extends Component {}
