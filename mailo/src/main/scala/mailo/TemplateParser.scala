package mailo.parser

import mailo.MailoError

import scalaz.\/
import scalaz.syntax.either._

import mailo.MailRawContent

import util.matching.Regex

object ParserError {
  case object HtmlNotValid extends MailoError("Content was not valid HTML")
  case class DisjointParametersAndMatches(
    justParams: Set[String],
    justMatches: Set[String]
  ) extends MailoError("Disjoint parameters and matches, params (${justParams.toString}), matches (${jusetMatches.toString})")
  case class OverlappedParametersAndMatches(
    justParams: Set[String],
    justMatches: Set[String],
    overlap: Set[String]
  ) extends MailoError(s"Overlapped parameters and matches, but no exact match: just params (${justParams.toString}), just matches (${justMatches.toString}), overlapped params (${overlap.toString})")
  case class PartialsDoNotExist(partials: Set[String]) extends
      MailoError(s"Some of the provided partials do not exist, here is the list ${partials.toString}")
  case class TooFewPartialsProvided(partials: Set[String]) extends
      MailoError(s"Too few partials provided to the document, here is the list ${partials.toString}")
  case class TooFewParamsProvided(params: Set[String]) extends
    MailoError(s"Too few params provided to the document, here is the list ${params.toString}")
  case class TooManyParamsProvided(params: Set[String]) extends
    MailoError(s"Too many params provided to the document, here is the list ${params.toString}")
}

object HTMLParser {
  import ParserError._

  def parse(content: MailRawContent, params: Map[String, String]): \/[MailoError, String] = {
    replaceAllTemplates(content.template, content.partials) flatMap (replaceAllParams(_, params))
  }

  private[this] def replaceAllTemplates(
    content: String,
    partials: Map[String, String]
  ): \/[MailoError, String] = {
    val mockPattern = """\[\[([^\s\\]+)\]\]""".r

    val matches = mockPattern findAllMatchIn(content) map (_.group(1))

    val partialsSet: Set[String] = partials.keySet
    val matchesSet: Set[String] = matches.toSet

    if (matchesSet subsetOf partialsSet) safelyReplaceAllInDocument(content, partials, mockPattern).right[MailoError]
    else PartialsDoNotExist(matchesSet -- partialsSet).left[String]
  }

  private[this] def replaceAllParams(
    document: String,
    params: Map[String, String]
  ): \/[MailoError, String] = {
    val parameterPattern = """\{\{([^\s\\]+)\}\}""".r

    val matches = parameterPattern findAllMatchIn (document) map (_.group(1))

    val paramsSet: Set[String] = params.keySet
    val matchesSet: Set[String] = matches.toSet

    if (paramsSet == matchesSet) safelyReplaceAllInDocument(document, params, parameterPattern).right[MailoError]
    else if (matchesSet subsetOf paramsSet) TooManyParamsProvided(paramsSet -- matchesSet).left[String]
    else if (paramsSet subsetOf matchesSet) TooFewParamsProvided(matchesSet -- paramsSet).left[String]
    else if ((paramsSet intersect matchesSet).isEmpty) DisjointParametersAndMatches(paramsSet, matchesSet).left[String]
    else OverlappedParametersAndMatches(paramsSet -- matchesSet, matchesSet -- paramsSet, matchesSet intersect paramsSet).left[String]
  }

  private[this] def safelyReplaceAllInDocument(
    document: String,
    values: Map[String, String],
    pattern: Regex
  ): String = {
    import scala.util.matching.Regex.Match
    def replacement(m: Match) = {
      import java.util.regex.Matcher
      require(m.groupCount == 1)
      Matcher.quoteReplacement( values(m group 1) )
    }

    pattern replaceAllIn(document, replacement _)
  }
}

object HTMLValidator {
  import ParserError._

  def validate(document: String): MailoError \/ String = document.right[MailoError]
}
