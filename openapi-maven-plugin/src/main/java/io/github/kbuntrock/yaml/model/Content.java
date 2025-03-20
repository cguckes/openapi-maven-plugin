package io.github.kbuntrock.yaml.model;

import io.github.kbuntrock.model.DataObject;
import io.github.kbuntrock.model.ParameterObject;
import io.github.kbuntrock.utils.OpenApiTypeResolver;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Content {
	private Schema schema;

	public static Content fromMultipartBodies(final List<ParameterObject> parameterObjects){
		final Content content = new Content();

		content.schema = new Schema();
		content.schema.setType(OpenApiTypeResolver.OBJECT_TYPE);
		content.schema.setRequired(
			parameterObjects.stream()
				.filter(ParameterObject::isRequired)
				.map(ParameterObject::getName)
				.collect(Collectors.toList()));

		content.schema.properties = parameterObjects.stream()
			.collect(Collectors.toMap(
				ParameterObject::getName,
				po-> new Property(fromDataObject((DataObject) po).schema)));

		return content;
	}

	public static Content fromDataObject(final DataObject dataObject) {
		if(dataObject == null) {
			return null;
		}
		final Set<String> exploredSignatures = new HashSet<>();
		final Content content = new Content();
		content.schema = new Schema(dataObject, exploredSignatures);
		return content;
	}

	public Schema getSchema() {
		return schema;
	}
}
