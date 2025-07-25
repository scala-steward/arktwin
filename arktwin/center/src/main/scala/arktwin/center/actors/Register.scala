// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 TOYOTA MOTOR CORPORATION
package arktwin.center.actors

import arktwin.center.services.*
import arktwin.common.util.BehaviorsExtensions.*
import arktwin.common.util.CommonMessages.Nop
import arktwin.common.util.MailboxConfig
import org.apache.pekko.actor.typed.SpawnProtocol.Spawn
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import java.util.regex.PatternSyntaxException
import scala.collection.mutable
import scala.util.Random

object Register:
  type Message = CreateEdge | CreateAgents | UpdateAgents | DeleteAgents | AddSubscriber |
    RemoveSubscriber | Nop.type
  case class CreateEdge(request: CreateEdgeRequest, replyTo: ActorRef[CreateEdgeResponse])
  case class CreateAgents(
      requests: CreateAgentsRequest,
      replyTo: ActorRef[CreateAgentsResponse]
  )
  case class UpdateAgents(request: RegisterAgentsPublish)
  case class DeleteAgents(agentSelector: AgentSelector)
  case class AddSubscriber(edgeId: String, subscriber: ActorRef[RegisterAgentsSubscribe])
  case class RemoveSubscriber(edgeId: String)

  def spawn(runId: String): ActorRef[ActorRef[Message]] => Spawn[Message] =
    Spawn(apply(runId), getClass.getSimpleName, MailboxConfig(this), _)

  // single substitution cipher so that clients cannot depend on definitive IDs
  private val idSuffixCharacters = Random.shuffle("0123456789abcdefghijklmnopqrstuvwxyz")
  private def issueId(prefix: String, n: Int): String =
    val suffix = mutable.StringBuilder()
    var temp = n
    while temp > 0 do
      suffix.insert(0, idSuffixCharacters(temp % idSuffixCharacters.size))
      temp /= idSuffixCharacters.size
    (if prefix.isEmpty() then "" else prefix + "-") + suffix.toString()

  def apply(
      runId: String
  ): Behavior[Message] = Behaviors.setupWithLogger: (context, logger) =>
    var edgeNum = 0
    var agentNum = 0
    val agents = mutable.Map[String, RegisterAgent]()
    var subscribers = Map[String, ActorRef[RegisterAgentsSubscribe]]()

    Behaviors.receiveMessage:
      case CreateEdge(request, replyTo) =>
        edgeNum += 1
        val id = issueId(request.edgeIdPrefix, edgeNum)
        replyTo ! CreateEdgeResponse(id, runId)
        Behaviors.same

      case CreateAgents(requests, replyTo) =>
        val newAgents = for request <- requests.requests yield
          agentNum += 1
          val id = issueId(request.agentIdPrefix, agentNum)
          RegisterAgent(id, request.kind, request.status, request.assets)
        replyTo ! CreateAgentsResponse(newAgents.map(a => CreateAgentResponse(a.agentId)))
        for subscriber <- subscribers.values do subscriber ! RegisterAgentsSubscribe(newAgents)
        agents ++= newAgents.map(a => a.agentId -> a)
        Behaviors.same

      case UpdateAgents(request) =>
        for subscriber <- subscribers.values do subscriber ! RegisterAgentsSubscribe(request.agents)
        for agent <- request.agents do
          for oldAgent <- agents.get(agent.agentId) do
            agents(agent.agentId) = RegisterAgent(
              agent.agentId,
              oldAgent.kind,
              oldAgent.status ++ agent.status,
              oldAgent.assets
            )
        Behaviors.same

      case DeleteAgents(agentSelector) =>
        val deletingIds = agentSelector match
          case AgentIdSelector(regex) =>
            try
              val r = regex.r
              agents.keys.filter(r.matches)
            catch
              case e: PatternSyntaxException =>
                logger.warn(e.getMessage)
                Seq()
          case AgentKindSelector(regex) =>
            try
              val r = regex.r
              agents.filter(a => r.matches(a._2.kind)).keys
            catch
              case e: PatternSyntaxException =>
                logger.warn(e.getMessage)
                Seq()
          case _ =>
            Seq()
        for subscriber <- subscribers.values do
          subscriber ! RegisterAgentsSubscribe(
            deletingIds.map(RegisterAgentDeleted.apply).toSeq
          )
        agents --= deletingIds
        Behaviors.same

      case AddSubscriber(edgeId, subscriber) =>
        subscriber ! RegisterAgentsSubscribe(agents.values.toSeq)
        context.watchWith(subscriber, RemoveSubscriber(edgeId))
        subscribers += edgeId -> subscriber
        Behaviors.same

      case RemoveSubscriber(edgeId) =>
        subscribers -= edgeId
        Behaviors.same

      case Nop =>
        Behaviors.same
