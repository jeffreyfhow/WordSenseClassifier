
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

//XML parser libraries
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Classifier {
	private final String dictionaryFile = "inputFiles/EnglishLS.dictionary2.xml";
	private final String testFile = "inputFiles/EnglishLS.test";
	private final String trainFile = "inputFiles/EnglishLS.train";
	private final String keyFile = "inputFiles/EnglishLS.test.key";
	private String outputFile = "outputFiles/output.txt";
	private String analysisFile = "outputFiles/analysis.txt";
	private final String stopFile = "inputFiles/common-english-words.txt";
	private final int MAX_WINDOW_SIZE = 4;
	private final double SMOOTHING_DELTA = 1;
	private String testWord;
	private Hashtable<String,SenseStatistic> senseStats;
	private TreeSet<String> vocab;
	private TreeMap<String,String> outResults;
	private TreeSet<String> stopWords;
	private int occurrenceTtl;
	
	//********************************************************
	//						INIT
	//********************************************************
	public Classifier(String word){
		outputFile = "outputFiles\\output_" + word + ".txt";
		analysisFile = "outputFiles\\analysis_" + word + ".txt";
		testWord = word;
		occurrenceTtl = 0;
		senseStats = new Hashtable<String,SenseStatistic>();
		vocab = new TreeSet<String>();
		stopWords = new TreeSet<String>();
		outResults = new TreeMap<String,String>();
		try{
			PrintWriter writer = new PrintWriter(outputFile);
			writer.print("");
			writer.close();
			writer = new PrintWriter(analysisFile);
			writer.print("");
			writer.close();
			Scanner s = new Scanner(new FileInputStream(stopFile));
			StringTokenizer st = new StringTokenizer(s.nextLine(),",");
			while(st.hasMoreTokens()){
				stopWords.add(st.nextToken());
			}
			s.close();
		}
		catch(FileNotFoundException e){
			System.out.println("Error: File not found.");
		}
	}
	
	
	//********************************************************
	//						TRAIN
	//********************************************************
	public void trainFile(){
		initSensesFromDictionary(getLexeltElement(dictionaryFile));
		Element e = getLexeltElement(trainFile);
		NodeList instanceList = e.getElementsByTagName("instance");
		int instanceCount = instanceList.getLength();
		for (int i = 0; i < instanceCount; i++) {
			Node currentNode = instanceList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
            	if(trainInstance((Element)currentNode)){
            		occurrenceTtl++;
            	}
            }
		}
	}
	
	private void initSensesFromDictionary(Element e){
		NodeList senseList = e.getElementsByTagName("sense");
		int senseCount = senseList.getLength();
		for (int i = 0; i < senseCount; i++) {
            Node currentNode = senseList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
            	Element s1 = (Element)currentNode;
            	String id = s1.getAttribute("id");
            	String synset = s1.getAttribute("synset");
            	String gloss = s1.getAttribute("gloss");
            	
            	senseStats.put(id,new SenseStatistic(id, synset, gloss));
            }
        }
		senseStats.put("U", new SenseStatistic("U", "U", "U"));
	}
	
	public boolean trainInstance(Element e){
		String id = e.getAttribute("id");
		
		//Get context words
		Node contextNode =  e.getElementsByTagName("context").item(0);
		String context = contextNode.getTextContent();
		String head = ((Element)contextNode).getElementsByTagName("head").item(0).getTextContent();
		String[] contextWords = tokenizeString(context,head);
		
		//Get answers
		NodeList answerList = e.getElementsByTagName("answer");
		boolean addInstance = false;
		int answerCount = answerList.getLength();
		for (int i = 0; i < answerCount; i++) {
            Node currentNode = answerList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
            	Element s1 = (Element)currentNode;
            	String senseid = s1.getAttribute("senseid");
            	//Get sense statistic and add data
            	SenseStatistic currStat = senseStats.get(senseid);
            	currStat.insertContextWords(contextWords);
            	currStat.incrementOccurrences();
            	//add context word to vocabulary
            	for(int j = 0; j < contextWords.length; j++){
            		vocab.add(contextWords[j]);
            	}
            	addInstance = true;
            }
		}
		
		return addInstance;
	}
	
	//********************************************************
	//						TEST
	//********************************************************
	public void testFile(){
		if(senseStats.size()<1){
			System.out.println("Error: Need to train the classifier first.");
			return;
		}
		PrintWriter pw = null;
		Element e = getLexeltElement(testFile);
		NodeList instanceList = e.getElementsByTagName("instance");
		int instanceCount = instanceList.getLength();
		
		try{
			pw = new PrintWriter(new FileOutputStream(outputFile));
			for (int i = 0; i < instanceCount; i++) {
				pw.println(testInstance(e.getAttribute("item"),(Element)instanceList.item(i)));
			}
		}
		catch(FileNotFoundException exception){
			System.out.println("Error: output file not found");
		}
		pw.close();
	}
	
	private String testInstance(String word, Element e){
		String instanceId = e.getAttribute("id");
		String outputString = word + " " + instanceId + " ";
		Node contextNode =  e.getElementsByTagName("context").item(0);
		String head = ((Element)contextNode).getElementsByTagName("head").item(0).getTextContent();
		String[] contextWords = tokenizeString(
				e.getElementsByTagName("context").item(0).getTextContent(),head);
		//Update vocab for non-existent words
		int notInVocabCnt = 0;
		for(int i = 0; i < contextWords.length; i++){
			if(!vocab.contains(contextWords[i])){
				notInVocabCnt++;
			}
		}
		
		//For each sense, get a score --> keep top score/sense
		Iterator<Entry<String, SenseStatistic>> it = senseStats.entrySet().iterator();
		Double topScore = null;
		SenseStatistic topSense = null;
		SenseStatistic currSense = null;
		Double currScore = null;
		while (it.hasNext()) {
			Map.Entry<String, SenseStatistic> entry = it.next();
			currSense = entry.getValue();
			currScore = calcScore(currSense, contextWords, notInVocabCnt);
			if(topSense == null || currScore > topScore){
				topSense = currSense;
				topScore = currScore;
			}
		}
		if(topSense == null){
			System.out.println("No top sense");
			return null;
		}
		else{
			String sense = topSense.getSenseId();
			outResults.put(instanceId, sense);
			return outputString + sense;
		}
	}
	
	private double calcScore(SenseStatistic s, String[] context, int vocabOffset){
		//Calculate Probability
		double probability = Math.log((double)s.getOccurrences()/occurrenceTtl);
		double contextWdTtl = s.getContextWdsTtl();
		double vocabSize = vocab.size()+vocabOffset;
		//Calculate Conditional Probabilities
		for(int i = 0; i < context.length; i++){
			probability += 
					Math.log(
							((double)s.getContextWordCount(context[i]) + SMOOTHING_DELTA)/
							(contextWdTtl + (SMOOTHING_DELTA*vocabSize)));
		}
		return probability;
	}
	
	//********************************************************
	//						ANALYSIS
	//********************************************************
	//Analysis Function
	public void compareToKey(){
		PrintWriter pw = null;
		Scanner scanner = null;
		try{
			pw = new PrintWriter(new FileOutputStream(analysisFile));
			scanner = new Scanner(new FileInputStream(keyFile));
		}
		catch(FileNotFoundException exception){
			System.out.println("Error: analysis file not found");
		}
		String line = "";
		while(scanner.hasNextLine() && !line.contains(testWord)){
			line = scanner.nextLine();
		}
		
		//Iterate through file and compare to outputResults
		int ttlComparisons = 0;
		int ttlMatches = 0;
		while(scanner.hasNextLine() && line.contains(testWord)){
			ttlComparisons++;
			StringTokenizer tokens = new StringTokenizer(line);
			int tokenCnt = tokens.countTokens();
			boolean resultsMatch = false;
			tokens.nextToken();
			String currToken = tokens.nextToken();
			String instanceId = currToken;
			String instanceResult = outResults.get(currToken);
			while(tokens.hasMoreTokens()){
				currToken = tokens.nextToken();
				if(currToken.equals(instanceResult)){
					resultsMatch = true;
					ttlMatches++;
					break;
				}
			}
			pw.println(instanceId + "\t" + currToken + "\t" + instanceResult + "\t" + resultsMatch);
			line = scanner.nextLine();
		}
		
		pw.println("SUMMARY:");
		pw.println("Matches: " + ttlMatches);
		pw.println("Tests: " + ttlComparisons);
		pw.println("Percentage: " + (float)ttlMatches*100/ttlComparisons);
		
		System.out.println("SUMMARY:");
		System.out.println("Matches: " + ttlMatches);
		System.out.println("Tests: " + ttlComparisons);
		System.out.println("Percentage: " + (float)ttlMatches*100/ttlComparisons);
		
		pw.close();
		scanner.close();
	}
	
	
	//********************************************************
	//						HELPER FUNCTIONS
	//********************************************************
	public Element getLexeltElement(String fileName){
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	        DocumentBuilder db = dbf.newDocumentBuilder();
	        File file = new File(fileName);
	        if (file.exists()) {
	            Document doc = db.parse(file);
	            Element docEle = doc.getDocumentElement();
	
	            NodeList lexeltList = docEle.getElementsByTagName("lexelt");
	            int lexeltCount = lexeltList.getLength();

	            for (int i = 0; i < lexeltCount; i++) {
	                Node currentNode = lexeltList.item(i);
	                if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
	                	Element e = (Element)currentNode;
	                	String itemName = e.getAttribute("item");
	                	if(itemName.equals(testWord)){
	                		return e;
	                	}
	                }
	            }
	        }
	
	    } catch (SAXException e) {
	        System.out.println(e);
	        System.out.println("Terminating program...");
	        System.exit(1);
	    } catch (IOException e) {
	        System.out.println(e);
	        System.out.println("Terminating program...");
	        System.exit(1);
	    } catch (ParserConfigurationException e) {
	    	System.out.println(e);
	        System.out.println("Terminating program...");
	        System.exit(1);
		}
		System.out.println("Error: No such lexelt.");
		return null;
	}
	
	//Given a string, gets context words
	public String[] tokenizeString(String s, String head){
		StringTokenizer tokenizer = new StringTokenizer(s);
		int tokenCount = tokenizer.countTokens();
		LinkedList<String> contextWords = new LinkedList<String>();
		int targetWordIndex = 0;
		while(tokenizer.hasMoreTokens()){
			String currToken = tokenizer.nextToken();
			if(currToken.equals(head)){
				break;
			}
			
			if (isValidContextWord(currToken)) {
				contextWords.offer(currToken);
			}
			
			while(contextWords.size()>MAX_WINDOW_SIZE){
				contextWords.remove();
			}
			
			targetWordIndex++;
		}
		
		if(targetWordIndex>=tokenCount){
			//never found head
			System.out.println("didn't find head");
		}
		
		int countDown = MAX_WINDOW_SIZE;
		while(tokenizer.hasMoreTokens() && countDown > 0){
			String currToken = tokenizer.nextToken();
			if (isValidContextWord(currToken)) {
				contextWords.offer(currToken);
				countDown--;
			}
		}
		if(contextWords.size() > MAX_WINDOW_SIZE*2){
			System.out.println("Something odd in tokenizeString function.");
		}
		String[] cWordsArr = new String[contextWords.size()];
		return contextWords.toArray(cWordsArr);
	}

	//Word Validator
	private boolean isValidContextWord(String s){
		if(stopWords.contains(s.toLowerCase())){
			return false;
		}
		
		if (Pattern.matches("\\p{Punct}", s) || s.matches("-?\\d+(\\.\\d+)?")) {
			return false;
		}
		
		return true;
	}
}
