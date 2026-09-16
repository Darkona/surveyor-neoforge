package folk.sisby.surveyor.util;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public class TextUtil {
	public static Component stripInteraction(Component text) {
		MutableComponent mutable = text.copy();
		List<Component> siblings = mutable.getSiblings().stream().map(TextUtil::stripInteraction).toList();
		mutable.getSiblings().clear();
		mutable.getSiblings().addAll(siblings);
		return stripInteractionNonRecursively(mutable);
	}

	public static Component stripInteractionNonRecursively(Component text) {
		return text.copy().withStyle(s -> s.withHoverEvent(null).withClickEvent(null).withInsertion(null));
	}

	public static MutableComponent highlightStrings(Collection<String> list, Function<String, ChatFormatting> highlighter) {
		return Component.literal("[").append(ComponentUtils.formatList(
			list,
			Component.literal(", "),
			s -> Component.literal(s).setStyle(Style.EMPTY.applyFormat(Objects.requireNonNullElse(highlighter.apply(s), ChatFormatting.RESET)))
		)).append(Component.literal("]"));
	}
}
