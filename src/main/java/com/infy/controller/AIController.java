package com.infy.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class AIController {

	// ---------------------------------------------------------------------
	// SYSTEM PROMPT (SERVER-SIDE)
	// ---------------------------------------------------------------------
	// The system prompt defines the assistant's role, behavior, safety constraints,
	// and any strict output schema. It MUST be stored and managed on the server.
	// Clients must NOT provide or modify this value.
	//
	// Here it is applied once via ChatClient.Builder.defaultSystem(...) (see the
	// constructor) so every endpoint automatically uses it without repeating
	// .system(...) on each call. A structured multi-section persona is used on
	// purpose: it produces long, detailed output, which makes the streaming
	// endpoint's token-by-token "typing" effect clearly visible.
	private static final String SYSTEM_PROMPT = """
			You are an airline travel concierge. Help customers with flights, airports,
			cabin comfort, loyalty programmes, and travel disruptions.

			Reply using these sections (each on a new line):

			### Summary
			One sentence describing the traveller's situation.

			### Steps
			Numbered list of actions to take.

			### Tips
			2-3 bullet points of insider advice.

			### If Things Go Wrong
			One sentence on handling delays, cancellations, or lost baggage.
			""";

	private final ChatClient chatClient;

	public AIController(ChatClient.Builder chatClientBuilder) {

		// Build and reuse a ChatClient instance. ChatClient is auto-configured using
		// values from application.properties. defaultSystem(...) registers the system
		// prompt once so it is applied to every prompt() call made by this client.
		this.chatClient = chatClientBuilder
				.defaultSystem(SYSTEM_PROMPT)
				.build();
	}

	@GetMapping("/chat")
	public String chat(
			@Parameter(description = "A travel question or situation to get concierge guidance for", example = "What should I do if my connecting flight is delayed at Heathrow?") @RequestParam String userPrompt) {
		// -----------------------------------------------------------------
		// USER PROMPT (CLIENT-SIDE)
		// -----------------------------------------------------------------
		// The userPrompt comes from the client and represents the user's
		// request. The controller's job is to validate and accept only this
		// user-provided content. It must NOT accept arbitrary system prompts.
		Assert.hasText(userPrompt, "userPrompt is required");

		// Blocking call: waits for the full completion before returning.
		// The default system prompt is applied automatically (see constructor).
		return chatClient.prompt()
				.user(userPrompt.trim())
				.call()
				.content();
	}

	// ---------------------------------------------------------------------
	// STREAMING ENDPOINT
	// ---------------------------------------------------------------------
	// Demonstrates Spring AI's streaming API. Instead of blocking until the
	// full completion is ready (.call()), .stream() returns a reactive
	// Flux<String> that emits chunks of the response as the model generates
	// them. We expose them as Server-Sent Events (text/event-stream) so the
	// browser can render tokens incrementally for a "typing" effect.
	@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> stream(
			@Parameter(description = "A travel question or situation to get concierge guidance for", example = "What should I do if my connecting flight is delayed at Heathrow?") @RequestParam String userPrompt) {

		Assert.hasText(userPrompt, "userPrompt is required");

		return chatClient.prompt()
				.user(userPrompt.trim())
				.stream()
				.content();
	}

}
