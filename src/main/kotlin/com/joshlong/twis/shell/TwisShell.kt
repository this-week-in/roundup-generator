package com.joshlong.twis.shell

import com.beust.jcommander.Parameter
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.shell.jline.PromptProvider
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption
import org.springframework.stereotype.Service
import pinboard.Bookmark
import pinboard.PinboardClient
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*


/**
 * @author <a href="mailto:josh@joshlong.com">Josh Long</a>
 */
@EnableConfigurationProperties(RoundupConfigurationProperties::class)
@SpringBootApplication
class TwisShell {

	@Bean
	fun provider() = PromptProvider {
		AttributedString("roundup-generator => ",
				AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
	}
}

fun main(args: Array<String>) {
	runApplication<TwisShell>(*args)
}

object DateUtils {

	fun oneWeek(): Date {
		val now = LocalDateTime.now()
		val lastWeek = now.minusDays(8)
		return Date.from(lastWeek.toInstant(ZoneOffset.UTC))
	}
}

data class GenerateArguments(

		@Parameter(names = ["--tag"], description = "which tag would you like to use to seed the entries?", required = false)
		var tag: String = "twis",

		@Parameter(names = ["--from"], description = "from which date would you like to include (yyyy-MM-dd) entries?", required = false)
		var from: Date = DateUtils.oneWeek(),

		@Parameter(names = ["--to"], description = "to which date would you like to include (yyyy-MM-dd) entries?", required = false)
		var to: Date = Date(),

		@Parameter(names = ["--mark-as-processed"], description = "should we mark entries as processed?", required = false)
		var markAsProcessed: Boolean = false,

		@Parameter(names = ["--output"], description = """to which java.io.File should we write the generated report?
            (you may use this option or the --stdout option, but not both.)""", required = false)
		var outputFile: String = "${System.getProperty("user.home")}/Desktop/report.md",

		@Parameter(names = ["--stdout"], description = """write the generated report to stdout, which is the default behavior.
              (you may use this option or the --output option, but not both)""", required = false)
		var print: Boolean = false
)


@ShellComponent
class RoundupCommands(private val roundupService: RoundupService) {

	@ShellMethod("generate a roundup.")
	fun generate(@ShellOption(optOut = true) generateArguments: GenerateArguments) {

		val report = roundupService
				.generate(generateArguments.tag, generateArguments.from, generateArguments.to)
				.joinToString(System.lineSeparator())

		val out: OutputStream =
				if (generateArguments.print)
					System.out
				else
					File(generateArguments.outputFile).outputStream()

		val destination = if (generateArguments.print) "console" else """file "${generateArguments.outputFile}""""
		val msg = """writing the roundup for tag "${generateArguments.tag}" to the ${destination}."""
		println(msg)

		BufferedWriter(OutputStreamWriter(out)).use { writer -> writer.write(report) }

		if (generateArguments.markAsProcessed) {
			this.roundupService.markAsProcessed()
		}
	}
}

@ConfigurationProperties(prefix = "twi.roundup")
open class RoundupConfigurationProperties(var priorityHrefs: List<String> = mutableListOf(), var twiTag: String = "twis")

@Service
class RoundupService(private val pinboardClient: PinboardClient,
                     private val props: RoundupConfigurationProperties) {

	fun generate(tag: String, from: Date, to: Date): List<String> {

		val processedTag = "processed"

		fun comparableString(bookmark: Bookmark): String {
			val href = bookmark.href!!
			val contains = props.priorityHrefs.any { href.toLowerCase().contains(it.toLowerCase()) }
			val time = bookmark.time!!.time
			return """ ${contains} ${time} """
		}

		return pinboardClient
				.getAllPosts(arrayOf(tag), results = 1000, fromdt = from, todt = to)
				.filter { !it.tags.contains(processedTag) }
				.sortedWith(Comparator { a, b ->
					comparableString(b).compareTo(comparableString(a))
				})
				.map { bookmark ->

					val needsToBeProcessed = bookmark.extended!!.isNotBlank() &&
							(bookmark.extended!!.contains("_URL_") || bookmark.extended!!.contains("_TITLE_"))

					val processedDescription = if (needsToBeProcessed) {
						bookmark.extended!!
								.replace("_URL_", bookmark.href!!, true)
								.replace("_TITLE_", bookmark.description!!)
					} else {
						if (bookmark.description == bookmark.href) {
							bookmark.extended
						} else {
							bookmark.description
						}
					}
					val title = bookmark.description!!.trim()
					val url = bookmark.href!!
					val description: String = (if (processedDescription.isNullOrBlank())
						bookmark.extended!!
					else
						processedDescription!!)
							.trim()
					val shouldRenderDescription = title != description
					return@map if (!shouldRenderDescription) "[$title]($url)" else description
				}
				.map { "* ${it.trim()}" }
	}

	fun markAsProcessed() {

		val processedTag = "processed"

		pinboardClient
				.getAllPosts(tag = arrayOf(props.twiTag))
				.filter { !it.tags.contains(processedTag) }
				.forEach {

					val tags = arrayListOf<String>()
					tags.add(processedTag)
					it.tags.forEach { t -> tags.add(t) }

					val toArray: Array<String> = tags.toArray(
							arrayOfNulls(tags.size))!!

					pinboardClient.addPost(
							it.href!!,
							it.description!!,
							it.extended!!,
							toArray,
							it.time!!,
							true,
							it.shared,
							it.toread
					)
				}
	}
}