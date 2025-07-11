// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 TOYOTA MOTOR CORPORATION
package arktwin.edge.actors.sinks

import arktwin.center.services.ClockBase
import arktwin.common.util.BehaviorsExtensions.*
import arktwin.common.util.CommonMessages.Nop
import arktwin.common.util.MailboxConfig
import arktwin.edge.configs.StaticEdgeConfig
import org.apache.pekko.actor.typed.SpawnProtocol.Spawn
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.dispatch.ControlMessage

object Clock:
  type Message = Catch | Get | Nop.type
  case class Catch(clockBase: ClockBase)
  case class Get(replyTo: ActorRef[ClockBase]) extends ControlMessage

  def spawn(staticConfig: StaticEdgeConfig): ActorRef[ActorRef[Message]] => Spawn[Message] = Spawn(
    apply(staticConfig),
    getClass.getSimpleName,
    MailboxConfig(this),
    _
  )

  def apply(
      staticConfig: StaticEdgeConfig
  ): Behavior[Message] = Behaviors.withStash(staticConfig.clockInitialStashSize): buffer =>
    Behaviors.receiveMessage:
      case Catch(clockBase) =>
        buffer.unstashAll(active(clockBase))

      case message =>
        buffer.stash(message)
        Behaviors.same

  def active(
      initClockBase: ClockBase
  ): Behavior[Message] = Behaviors.withLogger: logger =>
    var clockBase = initClockBase
    logger.info(clockBase.toString)

    Behaviors.receiveMessage:
      case Catch(newClockBase) =>
        clockBase = newClockBase
        logger.info(clockBase.toString)
        Behaviors.same

      case Get(replyTo) =>
        replyTo ! clockBase
        Behaviors.same

      case Nop =>
        Behaviors.same
