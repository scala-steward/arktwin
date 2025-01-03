// SPDX-License-Identifier: Apache-2.0
// Copyright 2024 TOYOTA MOTOR CORPORATION
package arktwin.edge.configs

import arktwin.common.data.QuaternionEnuEx.*
import arktwin.edge.util.JsonDerivation
import cats.data.Validated.valid
import cats.data.ValidatedNec
import pureconfig.ConfigReader
import pureconfig.generic.derivation.EnumConfigReader
import sttp.tapir.*

sealed trait RotationConfig:
  def validated(path: String): ValidatedNec[String, RotationConfig]

object RotationConfig:
  given Schema[RotationConfig] =
    JsonDerivation.fixCoproductSchemaWithoutDiscriminator(Schema.derived)

case object QuaternionConfig extends RotationConfig:
  def validated(path: String): ValidatedNec[String, QuaternionConfig.type] =
    valid(this)

case class EulerAnglesConfig(
    angleUnit: EulerAnglesConfig.AngleUnit,
    order: EulerAnglesConfig.Order
) extends RotationConfig:
  def validated(path: String): ValidatedNec[String, EulerAnglesConfig] =
    valid(this)

object EulerAnglesConfig:
  enum AngleUnit derives EnumConfigReader:
    case Degree, Radian
  object AngleUnit:
    given Schema[AngleUnit] = Schema.derivedEnumeration[AngleUnit](encode = Some(_.toString))

  enum Order derives EnumConfigReader:
    case XYZ, XZY, YXZ, YZX, ZXY, ZYX
  object Order:
    given Schema[Order] = Schema.derivedEnumeration[Order](encode = Some(_.toString))
