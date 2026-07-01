/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.entrydeclarationoutcome.utils

import cats.Show
import play.api.libs.json.{Format, JsString, JsonValidationError, Reads, Writes}

import scala.reflect.ClassTag
import scala.deriving.Mirror

object Enums {

  inline def getAllInstances[E](using m: Mirror.SumOf[E]): List[E] =
    allInstances[m.MirroredElemTypes, m.MirroredType]

  inline def allInstances[ET <: Tuple, E]: List[E] = {
    import scala.compiletime.*

    inline erasedValue[ET] match
    {
      case _: EmptyTuple => Nil
      case _: (t *: ts) => summonInline[ValueOf[t]].value.asInstanceOf[E] :: allInstances[ts, E]
    }
  }

  inline def format[E: ClassTag : Show : Mirror.SumOf]: Format[E] =
    Format(reads, writes)

  inline def reads[E: ClassTag : Show : Mirror.SumOf]: Reads[E] =
    summon[Reads[String]].collect(JsonValidationError(s"error.expected.$typeName"))(parser)

  private def typeName[E: ClassTag]: String = summon[ClassTag[E]].runtimeClass.getSimpleName

  inline def parser[E: Mirror.SumOf](using show: Show[E]): PartialFunction[String, E] =
    getAllInstances[E].map(e => show.show(e) -> e).toMap

  def writes[E](using show: Show[E]): Writes[E] = {
    Writes(e => JsString(show.show(e)))
  }
}
