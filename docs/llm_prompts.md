Given this document
Give me some insights on FFmpeg, Whisper for the audio aspects, A lightweight OCR 
Ill eventually explain my approach
Give me clear cut instructions of why my method would be a decent one or not
Ill ask u in case I dont know something
Do u copy?

What I understand is that
The requirement is to check where the required text is to be displayed
This text would be user input and we need to find it in the shared video link

We dont need a brute force approach for solving this
We can break it down into simpler/ smallaer chunks 
Ill explain my idea in while
Until then
Answer these qns
1) Do we compute on the website (ig NO coz , these sites host the video)
2) What tools do i use in this case, in case i have to download the video

Copy
SO I thought of using Java and Python 
A polyglot type of architecture
Python has various inbuilt libraries
Based on this info
Java can act as an orchestrator
Also,
Java 21 has VirtualThreads that can help in concurrency 
 Apart from this we can store the metadata of the video shared as a JSON file for a temp time duration
What i understood is as such
we are batch processing the content
We will be checking if the text is present in the video
So what i thought was as such
1) We can compute the audio check first, 
In case i get the audio we note the timestamp , and start to run the OCR checksrun from the start to the given timestamp
This is crucial as we are looking for the 1st display of the text
It could be shown before the audio was heard.
If it does not give a the frame, we continue to do the audio aspects until the next time its  heard, and then check the video again
I can think of this more like a sliding window problem
I terminate the threads and tasks as soon as I encounter the frame.
So once we hit the required frame
We can gather the data and display the frame as per our needs

Well
The issue u flagged was why not i just search the last 10 seconds from the time stamp of the audio
it makes sense
but what is the probability of it not being there
What if the text was present in the opening sequence
As we are checking for the 1st instance of the frame
It would be better to start from the scratch
This is crucial because
We need not recheck the entire video again, if there is no audio
Do u copy??

What if not to check for all the 24 frames in a second as they mostly would be the same for a small time duration
We could be checking a single frame for a time duration of 1-2 seconds
This would not only reduce the computation load

SO ONCE WE FIND THE TIME STAMP
WE CAN CHECK IF THE FRAME WAS PRESENT A SECOND AGO?
Binary search on the 24 sub frames
That should be a decent approach i feel
Thoughts?

Copy that
I wanted to use threads, this should be easier to implement i feel
Zombie Prevention is a crucial yet basic requirement
Implementation of Other OS fundamentals would be great

Also
IF something fails or crashes
I want it to be handled pretty well
Something like a roll back
Re process the particular chunk or so
Memory management is crucial, minimal leaks would be great

I also want to implement the SOLID Principles
The basic idea is to make is extensible 
Interfaces would come in handy ig
The java logic is independent of the ML logic
I need to ensure that, in future if we migrate from RapidOCR to a much more efficient DL algo
I need not refactor my Java code
Also, We could use a simple HTML website to provide a frontend
Where a user can upload the link and the text he is searching for
To make it lightweight , lets stick to  Javalin framerwork
Also what are the ways to mitigate or resolve the issues we discussed earlier
Variable Frame Rate (VFR) Trap
Streaming a live .m3u8 or .mp4 link from an external server directly into Java memory buffers

Great
We discussed something about the fuzzy logic earlier
Give me a brief and how it helps
What threshold do u recommend and why so?

Copy that
Based on my understanding
I have got this structure ready for the the repo i shall be working upon
D:.
│   .gitignore
│   README.md
│   
├───backend-java
│   │   pom.xml
│   │   
│   └───src
│       └───main
│           └───java
│               └───com
│                   └───extractor
│                       │   App.java
│                       │   
│                       ├───api
│                       │       JobController.java
│                       │       LiveUpdateSender.java
│                       │       
│                       ├───core
│                       │       ChunkManager.java
│                       │       MediaExtractor.java
│                       │       TextRecognizer.java
│                       │       TwoPointerSearch.java
│                       │       
│                       ├───infrastructure
│                       │       CircuitBreaker.java
│                       │       FFmpegAdapter.java
│                       │       PythonOcrClient.java
│                       │       
│                       ├───model
│                       │       FrameResult.java
│                       │       JobState.java
│                       │       StreamMetadata.java
│                       │       
│                       └───util
│                               ImageHasher.java
│                               VirtualThreadPool.java
│                               
├───docs
│       llm_prompts.md
│       
├───frontend
│       app.js
│       index.html
│       style.css
│       
└───worker-python
        clean_image.py
        extract_text.py
        requirements.txt
        start_server.py
        

Based on this ask me any doubts or qns u have
This is just the basic structure and im open for changes and recommendations
Also, What are all the basic things u want me to have in place before starting the coding aspects
Give me all the requirements for this

The 2 pointer approach is not what u have mentioned
What i meant by the 2 pointer approach was that we need to keep a a video pointer and an audio pointer
The audio pointer goes to a certain timestamp and stops
Then the video pointer moved ahead and matches the audio pointer
The approach u have mentioned is to check the earliest frame
This is the binary search task

OK
I HAVE I THE THINGS PLACED
I have all the dependencies in place

Now its time to code 
Guide me with the coding aspects here
One file at a time

Great
What about the circuit breaker code
If the Python server crashes or times out, this trips and fails the job gracefully instead of letting Java wait forever

Next set of files would be the /core
ChunkManager.java: The memory protector. It calculates how to safely slice the video into small micro-batches 
MediaExtractor.java: An interface defining the contract for pulling media , extractAudio(), extractVisualFrames()

Next up
TextRecognizer- interface defining the contract for reading text from an image.
TwoPointerSearch - The absolute brain of the operation. It implements the stateful search algorithm we discussed, independently advancing the audio_cursor and visual_cursor to find the target without overlapping
Copy??

Next would the /infra
FFmpegAdapterImplements MediaExtractor,
It uses ProcessBuilder to chain yt-dlp, ffprobe, and ffmpeg, piping raw image bytes directly into Java's memory.
PythonOcrClient Implements TextRecognizer.java. It takes the raw frame bytes and sends an HTTP request to your Python ML worker.
CircuitBreaker -  fault-tolerance wrapper for  PythonOcrClient. If the Python server crashes or times out, this trips and fails the job gracefully instead of letting Java wait forever.

I think
Failed cannot be Crashed or Text Never Appeared
Separtae the two
Would give me clarity later while debugging

Great
The Java Part is completed at this point right?
Now we need to move to the next aspect of the main logic of the program
Lets keep the approach similar
Where i tell u exactly 
What i want to be implemented from the particular file in the specified root folder
Copy that??

I have created a branch called logical-aspects
This is where we would be dealing with the code building
Give me these files
start_server - it acts like an entry point
sets up the /ocr endpoint using Uvicorn to listen for HTTP POST requests from your Java backend
requirements- for the end user who would be using this 
clean_image - use opencv 
cnverts image bytes into grayscale , and makes it visble wrt to the background
if u doubts regarding this do clarify

Follow it with this now
extract_audio - dedicated script for the Whisper audio search and to handle the initial timestamp localization
extract_text - It loads the RapidOCR ONNX model (CPU optimized), scans the cleaned image matrix, and returns the bounding boxes, strings, and confidence scores in a JSON file

Dont hardcode the url or the text
it should be user driven
Copy??