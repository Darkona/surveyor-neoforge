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
import net.minecraft.network.chat.contents.TranslatableContents;

public class TextUtil {
	public static Component stripInteraction(Component text) {
		// Fix: arguments of a translation (a death message's weapon) carry their own hover events, e.g. SHOW_ITEM with a
		// registry-bound ItemStack that can't be saved without registries.
		MutableComponent mutable = text.getContents() instanceof TranslatableContents translatable ? stripArguments(text, translatable) : text.copy();
		List<Component> siblings = mutable.getSiblings().stream().map(TextUtil::stripInteraction).toList();
		mutable.getSiblings().clear();
		mutable.getSiblings().addAll(siblings);
		return stripInteractionNonRecursively(mutable);
	}

	private static MutableComponent stripArguments(Component text, TranslatableContents translatable) {
		Object[] args = translatable.getArgs();
		Object[] stripped = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			stripped[i] = args[i] instanceof Component argument ? stripInteraction(argument) : args[i];
		}
		MutableComponent mutable = MutableComponent.create(new TranslatableContents(translatable.getKey(), translatable.getFallback(), stripped));
		mutable.setStyle(text.getStyle());
		mutable.getSiblings().addAll(text.getSiblings());
		return mutable;
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
