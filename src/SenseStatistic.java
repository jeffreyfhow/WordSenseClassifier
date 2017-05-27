import java.util.TreeMap;
import java.util.regex.Pattern;

public class SenseStatistic {
	private String senseId;
	private String synset;
	private String gloss;
	private int occurrences;
	private TreeMap<String,Integer> contextWords;
	private int contextWdsTtl;

	//********************************************************
	//						INIT
	//********************************************************
	public SenseStatistic(String id, String synset, String gloss){
		senseId = id;
		this.synset = synset;
		this.gloss = gloss;
		contextWords = new TreeMap<String,Integer>();
		occurrences = 0;
		contextWdsTtl = 0;
	}
	
	//********************************************************
	//						ACCESSORS
	//********************************************************
	public String getSenseId() {
		return senseId;
	}
	public int getOccurrences() {
		return occurrences;
	}
	public int getContextWdsTtl() {
		return contextWdsTtl;
	}
	
	
	//********************************************************
	//						Functions
	//********************************************************
	public void insertContextWord(String word){
		if(contextWords.containsKey(word)){
			int currValue = contextWords.get(word);
			contextWords.put(word, currValue+1);
			contextWdsTtl++;
		}
		else{
			contextWords.put(word, 1);
		}
	}
	
	public void insertContextWords(String[] words){
		for(int i = 0; i < words.length; i++){
			insertContextWord(words[i]);
		}
	}
	
	public void incrementOccurrences(){
		occurrences++;
	}
	
	public Integer getContextWordCount(String contextWord){
		Integer value = contextWords.get(contextWord);
		if(value == null){
			return 0;
		}
		else{
			return value;
		}
	}
	
}
