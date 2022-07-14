package ValiPar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import Utilities.BioConcST_FileReader;

public class RequiredElem {

	private String name;
	private JsonNode required_elements;

	public RequiredElem(String name, int testID) {
		super();
		this.name = name;
		this.required_elements = requiredElementsReader(name, testID);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public JsonNode getRequired_elements() {
		return required_elements;
	}

	public void setRequired_elements(JsonNode required_elements) {
		this.required_elements = required_elements;
	}

	private JsonNode requiredElementsReader(String name,int testID) {
		String stringJson = new BioConcST_FileReader().fileReader("./experiment/test"+testID+"/valipar/required_elements/" + name + ".json");
		ObjectMapper objectMapper = new ObjectMapper();	
		JsonNode jsonNode = null;
		try {
			jsonNode = objectMapper.readTree(stringJson);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		return jsonNode;
	}

}
