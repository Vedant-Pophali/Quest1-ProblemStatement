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

We can now start working with the testing and implementation
Lets pick some random, easy youtube video
Give me the URL and what to search for
We will eventually move and check the url in the PDF as well dw


INFO:     127.0.0.1:52816 - "POST /api/v1/recognize-audio HTTP/1.1" 200 OK
INFO:     127.0.0.1:52816 - "POST /api/v1/recognize-text HTTP/1.1" 404 Not Found
INFO:     127.0.0.1:52816 - "POST /api/v1/recognize-text HTTP/1.1" 404 Not Found
INFO:     127.0.0.1:52816 - "POST /api/v1/recognize-text HTTP/1.1" 404 Not Found
I get this from the server logs
What does this indicate?

I understand now
So the 404 error is basically saying that there is no match for this frame
Initiating connection to Java orchestrator...
Job accepted. Assigned ID: 8099775a-e5a8-432f-89b9-e295d9919ea8
Lost connection to telemetry stream.
This is what i get in the front end
"Lost connection to telemetry stream" is very misleading
What could be a better msg to be displayed
and why so??

Live Pipeline Status
Initiating connection to Java orchestrator...
Job accepted. Assigned ID: 0e9b07cb-b8d1-4673-8130-5e899038004a
[INITIALIZING] Extracting raw stream URL from https://www.youtube.com/watch?v=BPX9v8F547k
[INITIALIZING] Fetching stream metadata via ffprobe...
[AUDIO_SEARCH_RUNNING] Launching Audio Pointer and Coarse Visual Scan...
[SUCCESS] Frame found! 00:00:55.801
Extraction Result
Timestamp: 00:00:55.801
Frame Number: 1339
Matched Text: Think Different
This works
Great
But I think there is more to the document
I thought I needed an image of the frame
Also
Keep in mind, if there is no image and the audio has been captured
We just return the audio timestamp and stop
This is the last option
We need to check for the video before, we need to check for the subtities and what not
Refer the document and meet those criteria
Explain the path we will be taking and ill give my pov 

Swap priority 2 and 3
Burned-in -  If the subtitles are permanently "burned" into the video pixels (like in movies or ) our Visual Pointer (OCR) already perfectly handles this ig
We still  show the frame image of the timestamp when the subtitle shows up
It treats them as visual text
so basically proirity 3 coincides with priortiy 1
correction to be noted here
priority 2 stays, As we discussed
as there is not text
We can just return the timestamp tbh

I hope we are not hardcoding any of the metadata parametes
Introduce a config.json file 
Guide plz

This is the latest snapshot of my codebase
Based on this info
What are the next steps we would be taking?
Give me a roadmap
With the reasoning behind it
Remember the fact that we do not hardcode any of the values
Copy??

The roadmap looks decent
The addition of the threshold % is a good upgrade
We keep the base as 85% 
We dont change it, but we are open to get it changed if the user feels the need so
this is gonna be based on the quality of the video
We need to keep in check that the % does not go above 100 and below a particular value
to prevent false positives & negatives
SO,There would be a simple text box that we can add in the ui
initialized to 85%
it can be modified
Give me code for these instructions

I have updated all the code files as u told
In the next phase
i would like to accomplish the IMAGE display in the frontend
This is crucial
As this is the fundamental requirment
Base64 encoding 
Sending raw binary image files over a Server-Sent Events (SSE) stream is messy and prone to corruption. Encoding it as Base64 allows us to embed the entire image directly into the JSON telemetry payload. The frontend can then instantly render it without needing a secondary file-download endpoint

Copy that
Have implemented the changes u gave
Based on this
Lets test if the image is displayed on the ui
We can proceed with the later phases

 Great news
The image is popping up on  the UI
Now lets move to the next phase 
This is the priority driven phase
We need to wire TwoPointerSearch.java and JobController.java to strictly obey your Priority 1 (Visual) and Priority 2 (Audio) rules.

The Action: We will adjust the logic flow so that Java caches the Audio Pointer's timestamp but does not immediately report success. It will wait for the Visual Pointer to finish its search.
If the Visual Pointer finds the text, Java fires a SUCCESS_VISUAL event containing the Base64 image and exact timestamp.
If the Visual Pointer exhausts the entire search space and finds nothing, Java will check the cached Audio result. If it exists, it fires a SUCCESS_AUDIO event with only the timestamp.
If both fail, it fires TEXT_NOT_FOUND.
The Reasoning: This guarantees that the system always attempts to deliver the highest-priority visual proof first, elegantly gracefully degrading to audio-only if the text is merely spoken, completely satisfying the problem statement constraints.

Can u give me some  links that i can use to test the edge cases we discussed

Video Stream URL
https://www.youtube.com/watch?v=9FnO3igOkOk
Match Accuracy Threshold (%)
85
Target Dialogue
You can't handle the truth
Start Extraction
Live Pipeline Status
Initiating connection to Java orchestrator...
Job accepted. Assigned ID: 8f86c8ff-36e0-4742-bea6-abe726981e2b
[INITIALIZING] Extracting raw stream URL from https://www.youtube.com/watch?v=9FnO3igOkOk
[INITIALIZING] Fetching stream metadata via ffprobe...
[AUDIO_SEARCH_RUNNING] Launching Audio Pointer and Coarse Visual Scan...
[SUCCESS_AUDIO] Text not found visually. Audio match located.
Extraction Result
Timestamp: 00:00:44.500

Frame Number: N/A (Audio Match Only)
Matched Text: You can't handle the truth
Target text was spoken, but did not appear visually on screen.  