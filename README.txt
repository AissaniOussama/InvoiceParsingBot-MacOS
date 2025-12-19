




InvoiceBot macOS



Pre-Requisite:
Having java 21 installed

Having the InvoiceBot Folder on your Desktop

Opening the Application through Terminal: 
java -jar /Users/***YOUR USER****/Desktop/InvoiceBot/build/libs/InvoiceBot-0.0.1-SNAPSHOT.jar

For me it was
java -jar /Users/oussamaaissani/Desktop/InvoiceBot/build/libs/InvoiceBot-0.0.1-SNAPSHOT.jar








Step by Step Usage Guide and LLM Setup:

1 Download and Install LM Studio: https://lmstudio.ai/download

2 After Opening, install Model (through the search bar in the top) : meta-llama-3.1-8b-instruct

3 Install and Load the Model

Check if „Reachable at: http://127.0.0.1:1234“ 
4 Click on the green Thing in the left tool bar and toggle Start Running

5 Open The Bot (see above)

6 Select the files you want to parse and/or have renamed from a Folder on your Computer

7 Click on Extract 

After a while, it will finish and you can 

8 Click on the Button Excel Export and either choose or create a new Folder

You will now have a Excel Sheet with the Extracted invoice data and 2 Folders (1 with failed and 1 with successful Pdfs)   the Failed ones you will have to do manually (should be like 3/10 and things like Sentry)
