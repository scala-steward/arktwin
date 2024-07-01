// SPDX-License-Identifier: Apache-2.0
// Copyright 2024 TOYOTA MOTOR CORPORATION
package arktwin.center.actors

object CommonMessages:
  object Complete
  case class Failure(throwable: Throwable, edgeId: String)
