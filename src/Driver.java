import java.util.ArrayList;
import java.util.Scanner;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class Driver {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		System.out.println("Welcome to Jeff's Word Sense Classifier!");
		System.out.println("Please enter one of the following words to test:");
		
		//Read Input File of word options
		Scanner scanner = null;
		try{
			scanner = new Scanner(new FileInputStream("inputFiles/wordChoices.txt"));
		}
		catch(FileNotFoundException e){
			System.out.println("Error: Input file not found");
			System.out.println("Terminating program...");
			System.exit(0);
		}
		
		String line = null;
		ArrayList<String> list = new ArrayList<String>();
		while(scanner.hasNextLine()){
			line = scanner.nextLine();
			System.out.println(line);
			list.add(line);
		}
		scanner.close();
	
		//Accept user input
		scanner = new Scanner(System.in);
		line = scanner.next();
		
		while(!list.contains(line)){
			System.out.println("Error: " + line + " is not an alternative.");
			System.out.println("Please enter one of the following words to test:");
			for(int i = 0; i < list.size(); i++){
				System.out.println(list.get(i));
			}
			line = scanner.next();
		}
		
		list = null;
		
		//Now that word is selected, create classifier
		
		Classifier c = new Classifier(line);
		System.out.println("TRAINING COMMENCED");
		c.trainFile();
		System.out.println("TESTING COMMENCED");
		c.testFile();
		System.out.println("ANALYSIS COMMENCED");
		c.compareToKey();
		System.out.println("Please refer to outputFiles directory for detailed output.");
		System.out.println("Please refer to WSD_Analysis.pdf for detailed analysis of output.");
		System.out.println("END");
	}

}
