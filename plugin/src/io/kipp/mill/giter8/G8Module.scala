package io.kipp.mill.giter8

import giter8.G8
import mill._
import mill.api.Result
import mill.define.Target
import mill.define.TaskModule
import os.Path

import java.nio.file.attribute.PosixFilePermission

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

  /** If no mill is detected in the template in order to run the
    * [[io.kipp.mill.giter8.G8Module.validationTargets]] aginst it we need Mill.
    * To make this easier, we just download millw, cache it, and use it.
    *
    * @return
    *   the PathRef to millw
    */
  private def downloadMill: T[PathRef] = T.task {
    val log = T.log
    val exeSuffix = if (scala.util.Properties.isWin) ".bat" else ""
    log.info(
      s"No mill found in template root, so falling back to millw${exeSuffix} to validate targets."
    )

    val url =
      s"https://raw.githubusercontent.com/lefou/millw/main/millw${exeSuffix}"

    val cacheDir = T.env
      .get("XDG_CACHE_HOME")
      .map(os.Path(_))
      .getOrElse(
        os.home / ".cache"
      ) / "mill-giter8"

    val cacheTarget = cacheDir / s"millw${exeSuffix}"

    os.makeDir.all(cacheDir)

    if (!os.exists(cacheTarget)) {
      log.info(
        s"No millw${exeSuffix} found in cache so downloading one for you"
      )

      val r = requests.get(url)
      if (r.is2xx) {
        os.write(cacheTarget, r.bytes)
        if (!scala.util.Properties.isWin) {
          os.perms.set(
            cacheTarget,
            os.perms(cacheTarget) + PosixFilePermission.OWNER_EXECUTE
          )
        }
      } else {
        Result.Failure(s"Unable to download millw${exeSuffix} when needed.")
      }
    }
    PathRef(cacheTarget)
  }

  /** The main target that you'll want to use. This will first ensure that
    * [[io.kipp.mill.giter8.G8Module.generate]] runs to ensure your project can
    * actually be generated with g8 and then run the
    * [[io.kipp.mill.giter8.G8Module.validationTargets]] against that project.
    *
    * @return
    *   the result of the validation
    */
  def validate: Target[String] = T {
    val projectPath = generate()
    val targets = validationTargets()
    val log = T.log

    val exeSuffix = if (scala.util.Properties.isWin) ".bat" else ""

    val mill = if (os.exists(projectPath / s"mill${exeSuffix}")) {
      s"./mill${exeSuffix}"
    } else if (os.exists(projectPath / s"millw${exeSuffix}")) {
      s"./millw${exeSuffix}"
    } else {
      downloadMill().path.toString()
    }

    val d = downloadMill()
    log.info(d.toString())
    val results: Seq[(String, Int)] = targets.zipWithIndex.map {
      case (command, id) =>
        log.info(
          s"""[${id + 1}/${targets.size}] attempting to run "${command}""""
        )
        // TODO do we want --no-server to be configurable?
        val cmd = os
          .proc(mill, "--no-server", command)
          .call(cwd = projectPath)

        (command, cmd.exitCode)
    }

    val (_, failed) = results.partition { case (_, 0) =>
      true
    }

    // TODO right now we assume that we want everythign to succeed. Should we?
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
  def generate: Target[Path] = T.command {
    val log = T.log
    val output = T.dest / "result"

    val rawProps: Either[String, G8.OrderedProperties] =
      if (!os.exists(propsFile().path)) {
        log.info(s"No default.properties file found so skipping...")
        Right(List.empty)
      } else {
        val in = os.read.inputStream(propsFile().path)
        // NOTE that readProps closes the stream
        Right(G8.readProps(in))
      }

    val result = for {
      raw <- rawProps
      mavenTransformed <- G8.transformProps(raw)
      fullyTransformed =
        mavenTransformed
          .foldLeft(G8.ResolvedProperties.empty) { case (resolved, (k, v)) =>
            resolved + (k -> G8.DefaultValueF(v)(resolved))
          }
      result <- G8.fromDirectory(
        templateDirectory = templateBase().toIO,
        workingDirectory = templateBase().toIO,
        arguments = fullyTransformed.map { case (key, value) =>
          s"--${key}=${value}"
        }.toSeq,
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
