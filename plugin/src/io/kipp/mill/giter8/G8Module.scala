package io.kipp.mill.giter8

import giter8.G8
import mill._
import mill.api.Result
import mill.define.Target
import mill.define.TaskModule
import os.Path

import scala.util.Failure
import scala.util.Success
import scala.util.Using

trait G8Module extends TaskModule {

  override def defaultCommandName(): String = "validate"

  /** We only support the `src` layout of g8, not the root layout. Meaning that
    * when a user tries to use your template, g8 expects a src/main/g8.
    * Therefore this should really never be changed or g8 won't be able to
    * actually find the template. That's why we don't expose it to the outside
    * world and instead force this structure.
    *
    * @return
    *   the root of your project where the g8 template should be found
    */
  private def templateBase = T.task(millOuterCtx.millSourcePath)

  /** Another opinionated choice, but we only support putting the
    * default.properties insides of src/main/g8/default.properties, not in the
    * project/ since the vast majority of Mill projects don't use project.
    * Therefore we don't expose this and instead just stick to it being in that
    * one place.
    *
    * @return
    *   the location of the default.properties file
    */
  private def propsFile =
    T.source(templateBase() / "src" / "main" / "g8" / "default.properties")

  /** A seq of targets that you'd like to test against the project generated
    * from your template.
    *
    * @return
    *   the seq of targets
    */
  def validationTargets: Target[Seq[String]] = T { Seq.empty[String] }

  /** The main target that you'll want to use. This will first ensure that
    * [[io.kipp.mill.giter8.generate]] runs to ensure your project can actually
    * be generated with g8 and then run the
    * [[io.kipp.mill.giter8.validationTargets]] against that project.
    *
    * @return
    *   the result of the validation
    */
  def validate = T {
    val projectPath = generate()
    val targets = validationTargets()
    val log = T.log

    val results: Seq[(String, Int)] = targets.zipWithIndex.map {
      case (command, id) =>
        log.info(
          s"""[${id + 1}/${targets.size}] attempting to run "${command}""""
        )
        // TODO right now we assume this is here, but we should download it if it's not
        // TODO we assume non-windows -- we'll need to account for .bat
        // TODO do we want --no-server to be configurable?
        val cmd = os
          .proc("./mill", "--no-server", command)
          .call(cwd = projectPath)

        (command, cmd.exitCode)
    }

    val (_, failed) = results.partition { case (_, 0) =>
      true
    }

    // TODO this assumes we want everything to suceed. Add in the ability to fail
    if (failed.isEmpty) {
      val msg = "All targets ran successfully!"
      log.info(msg)
      Result.Success(msg)
    } else {
      val errMsg = s"""|Some targets didn't succeed.
                       |
                       |${failed
                        .map(_._1)
                        .mkString("- ", "\n - ", "")}""".stripMargin
      log.error(errMsg)
      Result.Failure(errMsg)
    }
  }

  /** Task to generate a project from your template. This will also take into
    * account your default.properties and generate the project with all your
    * defaults.
    *
    * @return
    *   the location of where your project has been generated
    */
  def generate: Target[Path] = T {
    val log = T.log
    val output = T.dest / "result"

    val rawProps: Either[String, G8.OrderedProperties] =
      Using(os.read.inputStream(propsFile().path))(G8.readProps) match {
        case Failure(_) if !os.exists(propsFile().path) =>
          log.info(s"No default.properties file found so skipping...")
          Right(List.empty)
        case Failure(err) =>
          Left(
            s"""|Something went wrong when trying to read your default.properties.
                |
                |${err.getMessage()}""".stripMargin
          )

        case Success(value) => Right(value)
      }

    val result = for {
      raw <- rawProps
      props <- G8.transformProps(raw)
      result <- G8.fromDirectory(
        templateDirectory = templateBase().toIO,
        workingDirectory = templateBase().toIO,
        arguments = props.map { case (key, value) => s"--${key}=${value}" },
        forceOverwrite = true,
        outputDirectory = Some(output.toIO)
      )
    } yield result

    result match {
      case Left(err) =>
        log.error(err)
        Result.Failure(err)
      case Right(value) =>
        log.info(value)
        Result.Success(output)
    }
  }
}
