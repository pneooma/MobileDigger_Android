# MobileDigger Website Build Prompt

## Project Overview
I need you to help me build a modern, professional website for **MobileDigger** - an Android music organization app designed for DJs, music producers, and music enthusiasts. The app helps users quickly organize large music collections using intuitive swipe gestures.

---

## Website Requirements

### Technology Stack
- Modern, responsive HTML/CSS/JavaScript
- Mobile-first design
- Fast loading times
- SEO optimized
- Option to use: React, Vue, or vanilla HTML/CSS

### Pages Needed
1. **Home/Landing Page** - Hero section with app overview
2. **Features Page** - Detailed feature breakdown with demos
3. **How It Works** - Step-by-step usage guide with screenshots
4. **Download Page** - Google Play Store link and APK download
5. **Privacy Policy** - Legal compliance page
6. **About/Contact** - Developer info and support

---

## App Information

### What is MobileDigger?
MobileDigger is a fast music organizer for Android that allows users to:
- Organize thousands of audio files with **swipe gestures** (swipe right to like, left to dislike)
- Play music while sorting (no interruption to workflow)
- See **real-time waveforms** and **spectrograms** for audio analysis
- Create custom **subfolders** for organization (e.g., "Tech House", "Favorites")
- Support all major audio formats: **MP3, FLAC, AIFF, WAV, OGG, AAC, M4A**
- Work completely **offline** - no internet required, no data collection

### Target Audience
- DJs preparing sets
- Music producers organizing samples
- Music collectors with 1000+ audio files
- Anyone who needs to sort large music libraries quickly

### Key Value Proposition
"Organize music like a DJ - swipe to sort, see waveforms, work offline"

---

## Core Features (For Features Page)

### 🎵 PLAYBACK & ORGANIZATION
**Swipe to Organize**
- Swipe right to like → moves to "Liked" folder
- Swipe left to dislike → moves to "Rejected" folder
- Sort 100+ songs in minutes (faster than traditional file managers)
- Playback continues seamlessly when moving files

**Smart Miniplayer**
- Automatically appears when current track scrolls out of view
- Always shows what's actually playing (even after moving files)
- Sticky behavior for easy access to controls

**Playlist Management**
- TODO queue for unsorted files
- LIKED and REJECTED playlists
- Track "played-but-not-actioned" counter
- Custom subfolders for categories

### 🎨 VISUAL FEATURES
**Real-Time Waveforms**
- Visual representation of audio structure
- Seek by clicking/dragging on waveform
- Customizable height and opacity
- Works for all supported formats

**Professional Spectrograms**
- FFT-based frequency analysis
- Adjustable quality settings (Fast/Balanced/High)
- Share spectrograms as images
- Perfect for audio quality assessment

### 🎛️ ADVANCED FEATURES
**Multi-Select Mode**
- Select multiple tracks at once
- Batch like/dislike operations
- Process entire folders quickly

**File Operations**
- Create ZIP archives of liked songs
- Share playlists as text files
- Export selections for DJ software
- Move files to custom subfolders

**Search & Filter**
- Instant search across all playlists
- Find tracks by name or metadata
- Enter key to trigger manual search

**Theme Customization**
- 9 beautiful pastel themes (Android 16 style)
- Dark mode support
- Customizable waveform colors
- Modern Material 3 design

### 📊 TECHNICAL EXCELLENCE
**Format Support**
- MP3, FLAC, AIFF, WAV, M4A, AAC, OGG, WMA
- Professional AIFF support for lossless audio
- VLC + FFmpeg backends for maximum compatibility

**Performance Optimized**
- Handles 10,000+ files smoothly
- Automatic memory management
- Performance profiling built-in
- Low CPU usage, smooth animations

**Privacy First**
- No data collection whatsoever
- No internet required
- No analytics or tracking
- All processing happens locally
- Open source for transparency

**Background Playback**
- Music continues when app is minimized
- Full notification controls (Like/Dislike buttons in notification)
- Battery optimization support

---

## How It Works (Step-by-Step Guide)

### Setup (Screenshots Needed)
1. **Select Source Folder**
   - Tap "Select Source Folder"
   - Choose folder with music files to organize
   - App scans all audio files

2. **Choose Destination Folder**
   - Select where organized files should go
   - App creates "LIKED" and "REJECTED" subfolders
   - Can create custom subfolders (e.g., "Techno", "House")

### Daily Workflow
3. **Play and Swipe**
   - First track plays automatically (optional)
   - Listen to music
   - **Swipe right** → Like (moves to LIKED folder)
   - **Swipe left** → Dislike (moves to REJECTED folder)
   - Playback continues to next track

4. **View Waveforms**
   - Toggle waveform display on/off
   - Click waveform to seek
   - Visual identification of song structure

5. **Generate Spectrograms**
   - Tap "Analyze" button
   - View frequency spectrum
   - Share analysis as image

6. **Export Results**
   - Create ZIP of liked songs
   - Share playlist as text
   - Move selections to DJ software folder

---

## Demo Actions for Website (Video/GIF Ideas)

### Key Interactions to Show:
1. **Swipe Gesture Demo**
   - Show card being swiped right (green indicator)
   - Show card being swiped left (red indicator)
   - Demonstrate smooth animation

2. **Waveform Interaction**
   - Show waveform displaying audio structure
   - Demonstrate seeking by clicking waveform
   - Show progress line moving during playback

3. **Spectrogram Generation**
   - Click "Analyze" button
   - Show spectrogram popup with colorful frequency display
   - Demonstrate quality settings

4. **Miniplayer Behavior**
   - Show scrolling playlist
   - Miniplayer appears automatically when track scrolls away
   - Show controls still accessible

5. **Multi-Select Mode**
   - Select multiple tracks with checkboxes
   - Batch like/dislike action
   - Show progress indicator

6. **Theme Switching**
   - Open settings dialog
   - Switch between different themes
   - Show color changes throughout app

---

## Design Guidelines

### Color Scheme
**Primary Colors:**
- **Brand Color**: #4A90E2 (blue) or #5BC0BE (teal)
- **Accent**: #F67280 (coral/pink)
- **Success**: #8BC34A (green) for "Like"
- **Error**: #F44336 (red) for "Dislike"

**Background:**
- Light mode: Clean whites and light grays (#F5F5F5)
- Dark mode: Deep blues/blacks (#1A1A2E, #0F3460)

### Typography
- **Headings**: Bold, modern sans-serif (e.g., Inter, Poppins)
- **Body**: Clean, readable (e.g., Inter, Open Sans)
- **Code/Tech specs**: Monospace (e.g., Fira Code)

### Visual Style
- **Modern & Clean**: Material Design inspired
- **Music-Themed**: Waveforms, vinyl records, headphones as graphics
- **Professional**: Not too playful, suitable for DJs and producers
- **Minimalist**: Focus on functionality and features

---

## Homepage Structure

### Hero Section
**Headline**: "Organize Music Like a DJ"
**Subheadline**: "Sort thousands of audio files in minutes with intuitive swipe gestures. Professional waveforms, spectrograms, and complete privacy."

**CTA Buttons**:
- Primary: "Download on Google Play" (Google Play badge)
- Secondary: "Watch Demo" (opens video)
- Tertiary: "View on GitHub" (link to repository)

**Hero Image/Video**: 
- Phone mockup showing app in action
- Animated demo of swipe gesture
- Waveform background graphic

### Features Grid (3 columns)
**Quick Highlights:**
1. 🎵 **Swipe to Organize** - Sort music faster than ever
2. 📊 **Waveforms & Spectrograms** - Professional audio analysis
3. 🔒 **Privacy First** - No data collection, offline only
4. 🎨 **Beautiful Themes** - Customizable colors and layouts
5. ⚡ **Lightning Fast** - Handles 10,000+ files smoothly
6. 🎧 **All Formats** - MP3, FLAC, AIFF, WAV, and more

### Social Proof Section
**Stats to Display:**
- "Version 10.46 - Actively Developed"
- "Open Source on GitHub"
- "Supports 8+ Audio Formats"
- "10,000+ Files Capacity"

### Use Cases Section
**Who Uses MobileDigger:**
- DJs preparing sets and organizing record pools
- Music producers sorting samples and project files
- Collectors managing Beatport/Bandcamp purchases
- Radio DJs pre-listening to new releases

### Technical Specs
**Requirements:**
- Android 12+ (API 31+)
- 100MB free space
- Recommended: 2GB+ RAM

---

## Features Page Structure

### Detailed Feature Breakdown

#### Section 1: Core Workflow
**Header**: "Organize Music in 3 Simple Steps"
1. Select folders
2. Play and swipe
3. Export results

(Include screenshots for each step)

#### Section 2: Visual Features
**Waveforms**
- Screenshot of waveform display
- Explanation of seeking functionality
- Customization options

**Spectrograms**
- Screenshot of spectrogram popup
- Explanation of FFT analysis
- Quality settings demo

#### Section 3: Power User Features
**Multi-Select**
- Batch operations screenshot
- Efficiency comparison

**Custom Subfolders**
- Organization examples
- DJ workflow integration

**Search & Filter**
- Search interface screenshot
- Speed and accuracy

#### Section 4: Privacy & Performance
**Privacy Guarantees**
- No data collection
- No internet required
- No tracking/analytics
- Open source transparency

**Performance Metrics**
- 10,000+ file capacity
- Memory optimization
- Low battery impact
- Smooth animations

---

## Screenshots Needed (Priority Order)

### Essential (Minimum 8 for website):
1. **Main Player** - Playing track with waveform, showing Like/Dislike buttons
2. **Swipe Gesture** - Mid-swipe showing green (like) or red (dislike) indicator
3. **Playlist View** - List of tracks with grouped subfolders
4. **Waveform Close-up** - Clear view of waveform with playback position
5. **Spectrogram Popup** - Colorful frequency analysis display
6. **Smart Miniplayer** - Miniplayer at top with scrolled playlist
7. **Theme Selection** - Settings dialog showing multiple theme options
8. **Multi-Select Mode** - Multiple selected files with action buttons

### Additional (If Available):
9. Notification controls with Like/Dislike buttons
10. Folder selection interface
11. Search functionality
12. Custom subfolder creation
13. ZIP export progress
14. Visual settings dialog

---

## Download Page

### Google Play Store
**Primary CTA**: Google Play badge (official badge image)
**Link**: [Play Store URL when published]

### GitHub
**Secondary CTA**: "Download APK from GitHub"
**Link**: https://github.com/pneooma/MobileDigger_Android
**Version**: v10.46
**Release Date**: November 2024

### Installation Instructions
**For APK (Side-loading):**
1. Enable "Install from Unknown Sources" in Android settings
2. Download APK from GitHub releases
3. Open APK file to install
4. Grant necessary permissions

**Permissions Required:**
- File access (read/organize music)
- Notification (playback controls)
- Background playback (continue playing when minimized)

---

## Privacy Policy Page

### Quick Summary
**One-Sentence Promise**: "MobileDigger collects ZERO data - everything stays on your device."

### Full Privacy Policy
(Use the content from PRIVACY_POLICY.md - 285 lines covering):
- No data collection
- Permissions explanation
- Local storage only
- Third-party libraries
- GDPR/CCPA compliance
- Open source transparency

**Make it easily readable with:**
- Clear section headers
- Collapsible sections
- Icons for each permission
- "Why we need this" for each permission

---

## About/Contact Page

### Developer Info
**Name**: Alin Florescu
**GitHub**: https://github.com/pneooma
**Project**: MobileDigger - Open Source Music Organizer

### Contact Methods
**Email**: [Your email for support]
**GitHub Issues**: https://github.com/pneooma/MobileDigger_Android/issues
**Response Time**: Aim for 48 hours

### Project Story
**Inspiration**: 
"Inspired by DJ culture and the art of 'crate digging' - finding gems in large music collections. Traditional file managers are too slow for organizing music. MobileDigger combines playback with organization, allowing you to sort 100 songs in the time it takes to open a file browser."

**Development**: 
"Built with Kotlin, Jetpack Compose, and modern Android architecture. Over 10,000 lines of code optimized for performance and privacy."

**Open Source**:
"MobileDigger is open source to ensure transparency and allow community contributions. All code is auditable on GitHub."

---

## Additional Website Features

### FAQ Section
**Common Questions:**

**Q: Does MobileDigger require internet?**
A: No! The app works 100% offline. No data is sent anywhere.

**Q: What audio formats are supported?**
A: MP3, FLAC, AIFF, WAV, M4A, AAC, OGG, WMA - all major formats.

**Q: Is my music data collected?**
A: Absolutely not. Zero data collection. All processing is local.

**Q: Can I organize more than 1000 files?**
A: Yes! The app handles 10,000+ files smoothly with optimized memory management.

**Q: Does swiping delete my files?**
A: No! Swiping moves files to LIKED or REJECTED folders. Nothing is deleted unless you explicitly choose to.

**Q: Can I undo a swipe action?**
A: Yes! Use the undo button or manually move files back from liked/rejected folders.

**Q: Does it work with Beatport/Bandcamp downloads?**
A: Yes! Point MobileDigger to your downloads folder and organize away.

**Q: Can I use this for DJ sets?**
A: Absolutely! Many DJs use MobileDigger to pre-listen and organize record pools, Beatport charts, and promo downloads.

### Video Demo (If Available)
**30-60 Second Demo Video:**
- Show app opening and selecting folder
- Demonstrate swipe gesture (like/dislike)
- Show waveform and spectrogram
- Display miniplayer and playlist
- Show theme switching
- End with "Download Now" CTA

### Call-to-Action Sections
**Throughout the site, include CTAs:**
- "Try MobileDigger Today"
- "Download for Free"
- "View on GitHub"
- "Report a Bug"
- "Request a Feature"

---

## SEO Optimization

### Meta Information
**Title**: "MobileDigger - Fast Music Organizer for Android | DJ Tools"
**Description**: "Organize music like a DJ with MobileDigger. Swipe to sort, see waveforms, analyze spectrograms. Supports MP3, FLAC, AIFF. Privacy-first, offline, open source."

**Keywords**: 
- music organizer android
- DJ music sorting app
- audio file manager
- waveform viewer android
- spectrogram analyzer
- FLAC AIFF player
- DJ tools android
- music library organizer
- offline music app
- crate digging app

### Open Graph Tags (For Social Sharing)
**og:title**: MobileDigger - Organize Music Like a DJ
**og:description**: Fast music organizer with swipe gestures, waveforms, and spectrograms. Privacy-first and offline.
**og:image**: [Hero screenshot or feature graphic]
**og:url**: [Your website URL]

### Schema Markup
- Software Application schema
- Organization schema
- FAQ schema for FAQ section

---

## Technical Requirements

### Performance
- **Page Load Time**: Under 3 seconds
- **Mobile Performance**: 90+ on Google PageSpeed Insights
- **Accessibility**: WCAG 2.1 AA compliant

### Responsive Breakpoints
- **Mobile**: 320px - 767px
- **Tablet**: 768px - 1023px
- **Desktop**: 1024px+
- **Large Desktop**: 1440px+

### Browser Support
- Chrome/Edge (latest 2 versions)
- Firefox (latest 2 versions)
- Safari (latest 2 versions)
- Mobile browsers (iOS Safari, Chrome Mobile)

---

## Deliverables Needed

### Website Files
1. HTML pages (home, features, how-it-works, download, privacy, about)
2. CSS stylesheets (responsive, mobile-first)
3. JavaScript (animations, interactive elements)
4. Images folder (screenshots, icons, graphics)
5. README.md (deployment instructions)

### Assets to Create
1. Logo/icon variations
2. Feature graphics (1024x500 for hero)
3. Screenshot mockups (phone frames)
4. Animated GIFs (swipe gestures, waveforms)
5. Social media share images

### Optional Enhancements
1. Dark mode toggle
2. Animated background (subtle waveform animation)
3. Scroll animations (fade-in effects)
4. Interactive feature demos
5. Newsletter signup (for updates)

---

## Design Inspiration

### Similar Apps/Websites to Reference:
- **Shazam** - Clean music app design
- **Spotify** - Professional music platform UI
- **Rekordbox** (Pioneer DJ) - DJ software aesthetic
- **Audacity** - Audio tool branding
- **VLC Media Player** - Open source media player

### Design Principles:
✅ Clean and modern
✅ Music-themed without being cheesy
✅ Professional and trustworthy
✅ Easy to navigate
✅ Fast and performant
✅ Mobile-first approach

---

## Content Tone & Voice

### Writing Style
- **Professional but friendly**: Technical when needed, approachable overall
- **Action-oriented**: Use active voice, clear CTAs
- **Transparent**: Honest about features and limitations
- **Privacy-focused**: Emphasize no data collection
- **Concise**: Short paragraphs, bullet points, scannable

### Example Copy:
❌ Bad: "Our app is the best music organizer on the market with unparalleled features."
✅ Good: "Sort 100 songs in minutes with intuitive swipe gestures. See waveforms, analyze spectrograms, work offline."

---

## Launch Checklist

### Pre-Launch
- [ ] All pages completed and tested
- [ ] Screenshots added and optimized
- [ ] Mobile responsiveness verified
- [ ] Privacy policy published
- [ ] Contact info correct
- [ ] All links working
- [ ] SEO meta tags added
- [ ] Analytics setup (if desired)
- [ ] SSL certificate installed

### Post-Launch
- [ ] Submit to Google
- [ ] Share on social media
- [ ] Post on Reddit (r/Android, r/DJs, r/WeAreTheMusicMakers)
- [ ] Add website link to GitHub repo
- [ ] Update Google Play listing with website
- [ ] Monitor analytics

---

## Additional Context

### Current App Version: 10.46
**Latest Updates (v10.46):**
- Smart miniplayer shows correct file when moved to subfolder
- Miniplayer automatically appears when file not in current playlist
- Performance optimizations with memory profiling
- Better architecture with utility classes
- Enhanced error handling and recovery

### GitHub Repository
**URL**: https://github.com/pneooma/MobileDigger_Android
**Stars/Forks**: [Current stats]
**License**: [Specify license]
**Active Development**: Yes (regular updates)

### Community
**Feedback Welcome**: Open to feature requests and bug reports
**Contributions**: Open source - contributions welcome
**Support**: GitHub Issues for support requests

---

## Summary for AI Assistant

**Your Task**: Build a modern, professional website for MobileDigger Android app using the information above.

**Key Points**:
1. Music organization app for DJs and producers
2. Swipe gestures to sort (like/dislike)
3. Real-time waveforms and spectrograms
4. Privacy-first (no data collection, offline)
5. Open source on GitHub
6. Supports all major audio formats
7. Professional features for power users

**Design**: Clean, modern, music-themed, mobile-first
**Pages**: Home, Features, How It Works, Download, Privacy, About
**Priority**: Fast loading, SEO optimized, mobile responsive

**Please help me create a website that showcases these features beautifully and encourages users to download the app!**

---

## Questions to Ask Before Starting

1. Do you have screenshots/screen recordings I can use, or should I use placeholder images?
2. What is your preferred tech stack (React, Vue, vanilla HTML)?
3. Do you have a domain name already, or need help with hosting?
4. Do you want a single-page or multi-page website?
5. Should I include analytics (Google Analytics) or keep it minimal?
6. Do you have a logo/branding already, or should I suggest options?
7. What's your timeline for launch?
8. Do you need help deploying (GitHub Pages, Netlify, Vercel)?

---

**Ready to build an amazing website for MobileDigger! Let's make it happen! 🚀🎵**

