# CrosSight

## Overview
CrosSight is an Android mobile application that aids the visually impaired in crossing the street. 

### Developed by
1. Zenroy Chance
2. Alexis Ayuso

### About
It uses your device's camera to detect the pedestrian signal ahead of you and alerts the user when to wait or when to cross. Then, it naviagtes the user across the pedestrian crossing by telling them to stay straight or go left or go right.

#### Review (Video)
CrosSight by Alexis and Zenroy [https://youtu.be/5VqnkXPP07s?feature=shared]

### Award 
CrosSight received a 1st Place Award in 112 CSIE Undergraduate Project at National Dong Hwa University.

### Languages 
1. English
2. Chinese (Taiwan)

### Disclaimer
It is intended for use in Taiwan as crosswalks and pedestrian signals in other countries may vary. 

The APP is a tool to assist in crossing the street, but it does not replace traditional mobility aids like white canes and guide dogs. Please remain aware and use your discretion when crossing the street with or without CrosSight. 



## How Does It Work?
CrosSight leverages an object detection model to identify pedestrian signals and crosswalks. Then, it uses image processing to navigate the user on the crosswalk. 

### Features
In the CrosSight settings, users can enable and disable various alert preferences.

1. Screen Visuals:   flashes a green or red translucent color to indicate whether you can cross the street, and it displays phrases to navigate the user on the crosswalk.
2. Sound:   plays a ticking pattern for when to cross (fast ticking) and when to wait (slow ticking).
3. Voice Announcements:   announces how to navigate on the crosswalk.
4. Vibration:   vibrates when to cross (fast vibration) and when to wait (slow vibration).

### Accessibility
The UI/UX design allows for sight-free navigation around the application by utilizing TalkBack. TalkBack is an accessibility feature that helps people who are blind or have low vision to interact with Android devices using touch and spoken feedback.



## Object Detection Model
The model was trained on a dataset of pedestrain signals and crosswalks in China because of its similarity to those in Taiwan. 1904 images were used to train the model. A total of 7 classes were used to annotate the images. The model uses a MobileNetV2 architecture. 

### Results
•	Mean Average Precision (mAP) = 97.7%

•	Precision = 95.6%

•	Recall = 90.5%



## Hardware/Software Specifications
1. Camera Resolution = at least 12 MP
2. Operating System = Android 12 or later
3. Camera lens = clear (not blurry or covered)



