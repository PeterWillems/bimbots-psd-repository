package nl.tno.willemsph.psd_repository.property_set_definition;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.jena.query.ParameterizedSparqlString;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import nl.tno.willemsph.psd_repository.property_definition.PropertyDefinition;
import nl.tno.willemsph.psd_repository.property_definition.PropertyDefinitionInput;
import nl.tno.willemsph.psd_repository.sparql.EmbeddedServer;

@Component
public class PropertySetDefinitionRepository {
	private final static Logger LOGGER = Logger.getLogger(PropertySetDefinitionRepository.class.getName());

	public List<PropertySetDefinition> getAllPropertySetDefinitions() throws IOException {
		List<PropertySetDefinition> allPropertySetDefinitions = new ArrayList<>();
		List<String> allPropertySetDefinitionNames = getAllPropertySetDefinitionNames(null);
		for (String psetName : allPropertySetDefinitionNames) {
			PropertySetDefinition propertySetDefinition = new PropertySetDefinition(psetName);
			allPropertySetDefinitions.add(propertySetDefinition);
		}
		return allPropertySetDefinitions;
	}

	public PropertySetDefinition getOnePropertySetDefinition(String psetName) {
		PropertySetDefinition propertySetDefinition = new PropertySetDefinition(psetName);
		return propertySetDefinition;
	}

	private String getPropertySetDefinitionName(String psetId) throws IOException {
		ParameterizedSparqlString queryStr = new ParameterizedSparqlString(EmbeddedServer.getPrefixMapping());
		queryStr.setIri("pset", psetId);
		queryStr.append("SELECT ?name ");
		queryStr.append("WHERE {");
		queryStr.append("  ?pset IFC4-PSD:name ?name . ");
		queryStr.append("} ");
		JsonNode responseNodes = EmbeddedServer.instance.query(queryStr);
		if (responseNodes.size() > 0) {
			for (JsonNode node : responseNodes) {
				JsonNode nameNode = node.get("name");
				if (nameNode != null) {
					return nameNode.get("value").textValue();
				}
			}
		}
		return null;
	}

	public List<PropertySetDefinition> getAllPropertySetDefsForClass(String classId) throws IOException {
		List<PropertySetDefinition> allPropertySetDefinitions = new ArrayList<>();
		List<String> allPropertySetDefinitionNames = getAllPropertySetDefinitionNames(classId);
		for (String psetName : allPropertySetDefinitionNames) {
			PropertySetDefinition propertySetDefinition = new PropertySetDefinition(psetName);
			allPropertySetDefinitions.add(propertySetDefinition);
		}
		return allPropertySetDefinitions;
	}

	private List<String> getAllPropertySetDefinitionNames(String classId) throws IOException {
		ParameterizedSparqlString queryStr = new ParameterizedSparqlString(EmbeddedServer.getPrefixMapping());
		queryStr.append("SELECT ?pset ?name ");
		queryStr.append("WHERE {");
		if (classId == null) {
			queryStr.append("	?pset rdf:type IFC4-PSD:PropertySetDef ; ");
			queryStr.append("	   IFC4-PSD:name ?name . ");
		} else {
			queryStr.append("	?pset rdf:type IFC4-PSD:PropertySetDef ; ");
			queryStr.append("	   IFC4-PSD:name ?name . ");
			queryStr.setIri("classId", classId);
			queryStr.append("	  ?classId rdfs:subClassOf* ?parentClass . ");
			queryStr.append("	  ?pset IFC4-PSD:applicableClass ?parentClass . ");
			queryStr.append("	  ?parentClass rdf:type owl:Class . ");
			queryStr.append("FILTER ( ");
			queryStr.append("	!EXISTS { ?parentClass owl:unionOf ?union } ");
			queryStr.append("	&& !EXISTS {?parentClass rdfs:subClassOf expr:SELECT } ");
			queryStr.append("	&& ?parentClass != owl:Thing ");
			queryStr.append(") ");
		}
		queryStr.append("}");
		queryStr.append("ORDER BY ?name ");

		List<String> psetNames = new ArrayList<>();
		JsonNode responseNodes = EmbeddedServer.instance.query(queryStr);
		if (responseNodes.size() > 0) {
			for (JsonNode node : responseNodes) {
				JsonNode nameNode = node.get("name");
				if (nameNode != null) {
					String name = nameNode.get("value").asText();
					LOGGER.info("PSET: " + name);
					psetNames.add(name);
				}
			}
		}
		return psetNames;
	}

	public PropertySetDefinition createPropertySetDefinition(PropertySetDefinitionInput propertySetDefinitionInput)
			throws IOException {
		ParameterizedSparqlString queryStr = new ParameterizedSparqlString(EmbeddedServer.getPrefixMapping());
		queryStr.setIri("psd", propertySetDefinitionInput.getId());
		queryStr.setLiteral("name", propertySetDefinitionInput.getName());
		queryStr.append("INSERT { ");
		queryStr.append("  ?psd rdf:type IFC4-PSD:PropertySetDef . ");
		queryStr.append("  ?psd IFC4-PSD:name ?name . ");
		if (propertySetDefinitionInput.getDefinition() != null) {
			queryStr.setLiteral("definition", propertySetDefinitionInput.getDefinition().toString());
			queryStr.append("	?psd IFC4-PSD:definition ?definition . ");
		}
		if (propertySetDefinitionInput.getIfcVersion() != null) {
			queryStr.setLiteral("ifcVersion", propertySetDefinitionInput.getIfcVersion());
			queryStr.append(
					"	?psd IFC4-PSD:ifcVersion [ rdf:type IFC4-PSD:IfcVersion ; IFC4-PSD:version ?ifcVersion ] . ");
		}
		if (propertySetDefinitionInput.getPropertyDefs() != null
				&& propertySetDefinitionInput.getPropertyDefs().size() > 0) {
			for (PropertyDefinitionInput pDefInput : propertySetDefinitionInput.getPropertyDefs()) {
				queryStr.setIri("pd", pDefInput.getId());
				queryStr.setLiteral("pdName", pDefInput.getName());
				queryStr.append("  ?pd rdf:type IFC4-PSD:PropertyDef . ");
				queryStr.append("  ?pd IFC4-PSD:name ?pdName . ");
				queryStr.append("  ?psd IFC4-PSD:propertyDef ?pd; ");
			}
		}
		queryStr.append("} ");
		queryStr.append("WHERE { } ");

		EmbeddedServer.instance.update(queryStr);

		return getOnePropertySetDefinition(propertySetDefinitionInput.getName());
	}

	public PropertySetDefinition updatePropertySetDefinition(PropertySetDefinitionInput psetInput)
			throws IOException, URISyntaxException {
		String psetName = getPropertySetDefinitionName(psetInput.getId());
		if (psetName != null) {
			PropertySetDefinition psd = getOnePropertySetDefinition(psetName);
			psd.setId(psetInput.getId());
			if (!psd.getName().equals(psetInput.getName()) && psetInput.getName() != null
					&& psetInput.getName().length() > 0) {
				updateName(psd, psetInput);
			}

			PropertySetDefinitionResolver psdResolver = new PropertySetDefinitionResolver();
			psd.setDefinition(psdResolver.getDefinition(psd));
			if (psd.getDefinition() != null && !psd.getDefinition().equals(psetInput.getDefinition())) {
				updateDefinition(psd, psetInput);
			} else if (psd.getDefinition() == null && psetInput.getDefinition() != null) {
				updateDefinition(psd, psetInput);
			}

			return psd;
		}
		return null;
	}

	private void updateName(PropertySetDefinition psd, PropertySetDefinitionInput psdInput) throws IOException {
		ParameterizedSparqlString queryStr = new ParameterizedSparqlString(EmbeddedServer.getPrefixMapping());
		queryStr.setIri("psd", psd.getId());
		queryStr.setLiteral("origName", psd.getName());
		queryStr.setLiteral("newName", psdInput.getName());
		queryStr.append("DELETE { ");
		queryStr.append("  ?psd IFC4-PSD:name ?origName .");
		queryStr.append("} ");
		queryStr.append("INSERT { ");
		queryStr.append("  ?psd IFC4-PSD:name ?newName .");
		queryStr.append("} ");
		queryStr.append("WHERE { ?psd IFC4-PSD:name ?origName . } ");

		EmbeddedServer.instance.update(queryStr);
	}

	private void updateDefinition(PropertySetDefinition psd, PropertySetDefinitionInput psdInput) throws IOException {
		ParameterizedSparqlString queryStr = new ParameterizedSparqlString(EmbeddedServer.getPrefixMapping());
		queryStr.setIri("psd", psd.getId());
		if (psd.getDefinition() != null) {
			queryStr.setLiteral("origDefinition", psd.getDefinition());
			queryStr.append("DELETE { ");
			queryStr.append("  ?psd IFC4-PSD:definition ?origDefinition .");
			queryStr.append("} ");
		}
		if (psdInput.getDefinition() != null) {
			queryStr.setLiteral("newDefinition", psdInput.getDefinition());
			queryStr.append("INSERT { ");
			queryStr.append("  ?psd IFC4-PSD:definition ?newDefinition .");
			queryStr.append("} ");
		}
		queryStr.append("WHERE { ");
		if (psd.getDefinition() != null) {
			queryStr.append(" ?psd IFC4-PSD:definition ?origDefinition . ");
		}
		queryStr.append("} ");

		EmbeddedServer.instance.update(queryStr);
	}

	public boolean deletePropertySetDefinition(String psetId) throws IOException {
		ParameterizedSparqlString queryStr = new ParameterizedSparqlString(EmbeddedServer.getPrefixMapping());
		queryStr.setIri("psd", psetId);
		queryStr.append("DELETE { ");
		queryStr.append("  ?psd ?pred ?obj . ");
		queryStr.append("  ?obj rdf:type IFC4-PSD:PropertyDef ; IFC4-PSD:name ?pdName . ");
		queryStr.append("  ?sub ?inv ?psd . ");
		queryStr.append("} ");
		queryStr.append("WHERE { ");
		queryStr.append("  ?psd ?pred ?obj . ");
		queryStr.append("  OPTIONAL { ?obj rdf:type IFC4-PSD:PropertyDef ; IFC4-PSD:name ?pdName . } ");
		queryStr.append("  OPTIONAL { ?sub ?inv ?psd . } ");
		queryStr.append("} ");

		EmbeddedServer.instance.update(queryStr);

		return true;
	}

	public PropertyDefinition getPropertyDef(URI reqPropertyId) {
		return new PropertyDefinition(reqPropertyId.toString());
	}

}
