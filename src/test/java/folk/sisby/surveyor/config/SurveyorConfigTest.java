package folk.sisby.surveyor.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The config screen (the mod list's Config button) labels every option and section from en_us.json.
 */
class SurveyorConfigTest {
	private static void keys(UnmodifiableConfig config, List<String> out) {
		for (UnmodifiableConfig.Entry entry : config.entrySet()) {
			out.add(entry.getKey());
			if (entry.getValue() instanceof UnmodifiableConfig section) keys(section, out);
		}
	}

	@Test
	@DisplayName("Every config option and section has a config-screen label")
	void everyOptionLabelled() throws IOException {
		Path lang = Path.of(System.getProperty("surveyor.projectDir", "."), "src/main/resources/assets/surveyor/lang/en_us.json");
		JsonObject json = JsonParser.parseString(Files.readString(lang)).getAsJsonObject();
		List<String> keys = new ArrayList<>();
		keys(SurveyorConfig.SPEC.getValues(), keys);
		assertTrue(keys.size() > 20, "the spec lists its options");
		assertTrue(json.has("surveyor.configuration.title"));
		for (String key : keys) assertTrue(json.has("surveyor.configuration." + key), "no label for " + key);
	}
}
