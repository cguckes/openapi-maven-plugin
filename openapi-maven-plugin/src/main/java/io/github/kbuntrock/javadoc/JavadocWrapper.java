package io.github.kbuntrock.javadoc;

import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import com.github.javaparser.javadoc.description.JavadocDescription;
import com.github.javaparser.javadoc.description.JavadocDescriptionElement;
import com.github.javaparser.javadoc.description.JavadocInlineTag;
import com.github.javaparser.javadoc.description.JavadocSnippet;
import io.github.kbuntrock.utils.Logger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Kevin Buntrock
 */
public class JavadocWrapper {

	private static final String INHERIT_DOC_TAG_NAME = "inheritDoc";

	private static String endOfLineReplacement = null;

	private final Javadoc javadoc;

	private Map<JavadocBlockTag.Type, List<JavadocBlockTag>> blockTagsByType;
	private Map<String, JavadocBlockTag> paramBlockTagsByName;
	private boolean inheritTagFound = false;
	private boolean sortDone = false;

	public JavadocWrapper(final Javadoc javadoc) {
		this.javadoc = javadoc;
	}

	public static void setEndOfLineReplacement(final String endOfLineReplacement) {
		JavadocWrapper.endOfLineReplacement = endOfLineReplacement;
	}

	public Javadoc getJavadoc() {
		return javadoc;
	}

	public void sortTags() {
		if(sortDone) {
			return;
		}
		sortDone = true;
		blockTagsByType = new HashMap<>();
		paramBlockTagsByName = new HashMap<>();
		for(final JavadocBlockTag blockTag : javadoc.getBlockTags()) {
			final List<JavadocBlockTag> list = blockTagsByType.computeIfAbsent(blockTag.getType(), k -> new ArrayList<>());
			list.add(blockTag);
			if(JavadocBlockTag.Type.PARAM == blockTag.getType() && blockTag.getName().isPresent()) {
				paramBlockTagsByName.put(blockTag.getName().get(), blockTag);
			} else if(JavadocBlockTag.Type.UNKNOWN == blockTag.getType() && INHERIT_DOC_TAG_NAME.equals(blockTag.getTagName())) {
				inheritTagFound = true;
			}
		}
	}

	public Optional<JavadocBlockTag> getParamBlockTagByName(final String parameterName) {
		return Optional.ofNullable(paramBlockTagsByName.get(parameterName));
	}

	public Optional<JavadocBlockTag> getReturnBlockTag() {

		final List<JavadocBlockTag> list = blockTagsByType.get(JavadocBlockTag.Type.RETURN);
		if(list != null && !list.isEmpty()) {
			return Optional.of(list.get(0));
		}
		return Optional.empty();
	}

	public Optional<String> getSummary() {
		return processJavadocElements(
			elements -> elements
				.filter(onlyTagsOfType(JavadocTag.SUMMARY))
				.map(JavadocWrapper::toTagContent));
	}

	public Optional<String> getDescription() {
		return processJavadocElements(
			elements -> elements
				.filter(JavadocWrapper::onlySnippetsAndFormattingTags)
				.map(JavadocWrapper::formatJavadocText));
	}

	public boolean isInheritTagFound() {
		return inheritTagFound;
	}

	public void printParameters() {
		if(paramBlockTagsByName != null && !paramBlockTagsByName.isEmpty()) {
			Logger.INSTANCE.getLogger().debug("Parameters : ");
			for(final Entry<String, JavadocBlockTag> entry : paramBlockTagsByName.entrySet()) {
				Logger.INSTANCE.getLogger().debug(entry.getKey() + " : " + entry.getValue().getContent().toText());
			}
		}

	}

	public void printReturn() {
		if(blockTagsByType != null) {
			final Optional<JavadocBlockTag> returnTag = getReturnBlockTag();
			if(returnTag.isPresent()) {
				Logger.INSTANCE.getLogger().debug("Return : " + returnTag.get().getContent().toText());
			}
		}
	}

	private static String formatJavadocText(JavadocDescriptionElement javadocElement) {
		if(javadocElement instanceof JavadocInlineTag) {
			JavadocInlineTag tag = (JavadocInlineTag) javadocElement;
			switch(JavadocTag.fromString(tag.getName())) {
				case CODE:
					return "`" + tag.getContent().trim() + "`";
				case SEE:
					return "*" + tag.getContent().trim() + "*";
				case LINK:
					Pattern p = Pattern.compile("href=\"(.*?)\"");
					Matcher m = p.matcher(tag.getContent());
					String url = null;
					if (m.find()) {
						url = m.group(1);
					}
					return "[" + url + "](" + url + ")";
				default:
					return tag.getContent();
			}
		}

		return javadocElement.toText();
	}

	private Optional<String> processJavadocElements(Function<Stream<JavadocDescriptionElement>, Stream<String>> elementProcessor) {
		return Optional.ofNullable(javadoc.getDescription())
			.map(JavadocDescription::getElements)
			.map(Collection::stream)
			.map(elementProcessor)
			.map(s -> s.collect(Collectors.joining("\n")))
			.map(JavadocWrapper::removeNewlinesIfActivated)
			.filter(text -> !text.isEmpty())
			.map(String::trim);
	}

	private static Predicate<JavadocDescriptionElement> onlyTagsOfType(JavadocTag tag) {
		return e -> (e instanceof JavadocInlineTag && tag.toString().toLowerCase().equals(((JavadocInlineTag) e).getName()));
	}

	private static boolean onlySnippetsAndFormattingTags(JavadocDescriptionElement e) {
		return onlySnippets(e) || (
			e instanceof JavadocInlineTag &&
				JavadocTag.isFormattingTag(((JavadocInlineTag) e).getName()));
	}

	private static boolean onlySnippets(JavadocDescriptionElement e) {
		return e instanceof JavadocSnippet;
	}

	private static String toTagContent(JavadocDescriptionElement t) {
		return ((JavadocInlineTag) t).getContent().trim();
	}

	private static String removeNewlinesIfActivated(String text) {
		return endOfLineReplacement != null
			? text.replaceAll("\\r\\n", endOfLineReplacement).replaceAll("\\n", endOfLineReplacement)
			: text;
	}
}
