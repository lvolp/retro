package wiro
package server.akkaHttp

import AutowireErrorSupport._

import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Directive1, ExceptionHandler, Route }

import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._

import FailSupport._

import io.circe.Json

import scala.language.implicitConversions

import scala.util.{ Try, Success, Failure }
import scala.concurrent.Future

trait Router extends RPCServer with PathMacro with MetaDataMacro {
  def tp: Seq[String]
  def methodsMetaData: Map[String, MethodMetaData]
  def routes: autowire.Core.Router[Json]
  def path: String = tp.last

  def buildRoute: Route = handleExceptions(exceptionHandler) {
    pathPrefix(path) {
      methodsMetaData map {
        case (k, v @ MethodMetaData(OperationType.Command(_), _)) => command(k, v)
        case (k, v @ MethodMetaData(OperationType.Query(_), _))   => query(k, v)
      } reduce (_ ~ _)
    }
  }

  def exceptionHandler = ExceptionHandler {
    case f@FailException(_) => complete(f.response)
  }

  private[this] def requestToken: Directive1[Option[String]] = {
    val authDirective: Directive1[Option[String]] = headerValueByName("Authorization")
      .flatMap { header =>
        val TokenPattern = "Token token=(.+)".r
        header match {
          case TokenPattern(token) => provide(Some(token))
          case _                   => provide(None)
        }
      }
    authDirective.recover { x => provide(None) }
  }

  private[this] def operationName(operationFullName: String, methodMetaData: MethodMetaData): String =
    methodMetaData.operationType.name.getOrElse(operationFullName.split("""\.""").last)

  private[this] def query(operationFullName: String, methodMetaData: MethodMetaData): Route = {
    (pathPrefix(operationName(operationFullName, methodMetaData)) & pathEnd & get & parameterMap) { params =>
      requestToken { token =>
        val appliedRequest = Try(routes(autowire.Core.Request(
          path = operationFullName.split("""\."""),
          args = queryArgs(params, token)
        )))

        appliedRequest match {
          case Success(res) => complete(res)
          case Failure(f) => handleUnwrapErrors(f)
        }
      }
    }
  }

  //Generates POST requests
  private[this] def command(operationFullName: String, methodMetaData: MethodMetaData): Route = {
    (pathPrefix(operationName(operationFullName, methodMetaData)) & pathEnd & post & entity(as[Json])) { request =>
      requestToken { token =>
        val appliedRequest = Try(routes(autowire.Core.Request(
          path = operationFullName.split("""\."""),
          args = commandArgs(request, token)
        )))

        appliedRequest match {
          case Success(res) => complete(res)
          case Failure(f) => handleUnwrapErrors(f)
        }
      }
    }
  }

  def commandArgs(request: Json, token: Option[String]): Map[String, Json] =
    request.as[Map[String, Json]].right.get.withToken(token)

  def queryArgs(params: Map[String, String], token: Option[String]): Map[String, Json] = {
    import io.circe.syntax._
    params.mapValues(_.asJson).withToken(token)
  }

  implicit class PimpMyMap(m: Map[String, Json]) {
    def withToken(token: Option[String]): Map[String, Json] = token match {
      case Some(t) => m + ("token" -> Json.fromString(t))
      case None => m
    }
  }
}
