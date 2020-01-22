package io.buildo.tapiro

import io.buildo.metarpheus.core.intermediate.Route

import scala.meta._

object AkkaHttpMeta {
  val `class` = (
    `package`: Term.Ref,
    imports: Set[Term.Ref],
    controllerName: Type.Name,
    endpointsName: Term.Name,
    implicits: List[Term.Param],
    http4sEndpoints: List[Defn.Val],
    routes: Term,
  ) => {
    val tapirEndpoints = q"val endpoints = $endpointsName.create()"
    val akkaHttpEndpointsName = Term.Name(s"${controllerName.syntax}AkkaHttpEndpoints")
    q"""
    package ${`package`} {
      ..${imports.toList.map(i => q"import $i._")}
      import cats.effect._
      import cats.implicits._
      import cats.data.NonEmptyList
      import sttp.tapir.server.akkahttp._
      import sttp.tapir.Codec.{ JsonCodec, PlainCodec }
      import akka.http.scaladsl.server._
      import akka.http.scaladsl.server.Directives._

      object $akkaHttpEndpointsName {
        def routes(controller: $controllerName)(..$implicits): Route = {
          ..${tapirEndpoints +: http4sEndpoints :+ routes}
        }
      }
    }
    """
  }

  val routes = (head: Route, tail: List[Route]) => {
    val first = Term.Name(head.name.last)
    val rest = tail.map(a => Term.Name(a.name.last))
    q"List(..$rest).foldLeft[Route]($first)(_ ~ _)"
  }

  val endpoints = (routes: List[Route]) =>
    routes.flatMap { route =>
      val name = Term.Name(route.name.last)
      val endpointsName = Term.Select(Term.Name("endpoints"), name)
      val controllersName = Term.Select(Term.Name("controller"), name)
      val controllerContent =
        if (route.method == "post") Some(controllersName)
        else if (route.method == "get") {
          if (route.params.isEmpty) Some(controllersName)
          else Some(Term.Select(Term.Eta(controllersName), Term.Name("tupled")))
        } else None
      controllerContent.map { content =>
        val toRoute = Term.Apply(Term.Select(endpointsName, Term.Name("toRoute")), List(content))
        q"val ${Pat.Var(name)} = $toRoute"
      }
    }
}
