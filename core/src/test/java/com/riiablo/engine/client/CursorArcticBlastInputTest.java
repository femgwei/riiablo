package com.riiablo.engine.client;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.map.RenderSystem;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CursorArcticBlastInputTest extends RiiabloTest {
  @Test void busyCastStillReceivesHeldPointerAndReleaseFromTheRealInputSystem() {
    Input previous=Gdx.input;
    Input input=Gdx.input=mock(Input.class);
    AtomicBoolean down=new AtomicBoolean(true);
    when(input.isButtonPressed(Input.Buttons.RIGHT)).thenAnswer(call->down.get());
    Actioneer actions=mock(Actioneer.class);
    when(actions.hasArcticBlastSequence(0)).thenReturn(true);
    when(actions.hasCasting(0)).thenReturn(true);
    when(actions.hasSequence(0)).thenReturn(true);
    RenderSystem renderer=mock(RenderSystem.class);
    when(renderer.getSrc()).thenReturn(0);
    HoveredManager hovered=mock(HoveredManager.class);
    when(hovered.getHoveredAt(anyInt(),anyFloat(),anyFloat(),anyBoolean())).thenReturn(-1);
    ClientActionInputPort selected=mock(ClientActionInputPort.class);
    when(selected.action(Input.Buttons.RIGHT)).thenReturn(SkillId.ARCTIC_BLAST);
    when(selected.skill(SkillId.ARCTIC_BLAST)).thenReturn(Riiablo.files.skills.get(SkillId.ARCTIC_BLAST));
    ClientPointerUiPort ui=mock(ClientPointerUiPort.class);
    IsometricCamera iso=mock(IsometricCamera.class,RETURNS_DEEP_STUBS);
    CursorMovementSystem cursor=new CursorMovementSystem();
    World world=new World(new WorldConfigurationBuilder().with(cursor).build()
        .register("iso",iso).register("map",new com.riiablo.map.Map(0,0))
        .register("itemController",mock(com.riiablo.save.ItemController.class))
        .register("clientActionInput",selected).register("pointerUi",ui));
    cursor.actioneer=actions;cursor.renderer=renderer;cursor.hoveredManager=hovered;
    try {
      world.create();world.setDelta(1f/25f);
      world.getSystem(CursorMovementSystem.class).capturePointerInput();world.process();
      verify(actions).updateArcticBlastInput(eq(0),eq(true),eq(-1),any());
      verify(actions,never()).cast(anyInt(),anyInt(),anyInt(),any());
      down.set(false);world.process();
      verify(actions).updateArcticBlastInput(eq(0),eq(false),eq(-1),any());
      when(actions.hasArcticBlastSequence(0)).thenReturn(false);
      when(actions.hasCasting(0)).thenReturn(false);
      when(actions.hasSequence(0)).thenReturn(false);
      when(actions.canInterrupt(0)).thenReturn(true);
      world.process();
      verify(actions,never()).cast(anyInt(),anyInt(),anyInt(),any());
      when(ui.blocksWorldInput()).thenReturn(true);
      clearInvocations(actions);world.process();
      verify(actions,never()).updateArcticBlastInput(anyInt(),anyBoolean(),anyInt(),any());
    } finally {world.dispose();Gdx.input=previous;}
  }
}
