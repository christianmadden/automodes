/**
*  Autophrases
*  Author: Christian Madden
*/

definition(
  name: "Autophrases",
  namespace: "christianmadden",
  author: "Christian Madden",
  description: "Automate Hello Home phrases based on time of day and presence",
  category: "Convenience",
  iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
  iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

preferences
{
  page(name: "prefsPage", title: "Automate Hello Home phrases based on time of day and presence.", uninstall: true, install: true)
}

def prefsPage()
{
  state.phrases = (location.helloHome?.getPhrases()*.label).sort()

  dynamicPage(name: "prefsPage")
  {
    section("When any of these people are home or away")
    {
      input "people", "capability.presenceSensor", title: "Which?", multiple: true, required: true
    }
    section("Run these phrases at sunrise and sunset when home or away")
    {
      input "sunrisePhrase", "enum", options: state.phrases, title: "Use this phrase at sunrise when home", required: true
      input "sunrisePhraseAway", "enum", options: state.phrases, title: "Use this phrase at sunrise when away", required: true
      input "sunsetPhrase", "enum", options: state.phrases, title: "Use this phrase at sunset when home", required: true
      input "sunsetPhraseAway", "enum", options: state.phrases, title: "Use this phrase at sunset when away", required: true
    }
    section("Run these phrases at a custom time when home or away (optional)")
    {
      input "customOneTime", "time", title: "Starts at this time every day", required: false
      input "customOnePhrase", "enum", options: state.phrases, title: "Use this phrase when home", required: false
      input "customOnePhraseAway", "enum", options: state.phrases, title: "Use this phrase when away", required: false
    }
    section("Run these phrases at a custom time when home or away (optional)")
    {
      input "customTwoTime", "time", title: "Starts at this time every day", required: false
      input "customTwoPhrase", "enum", options: state.phrases, title: "Use this phrase when home", required: false
      input "customTwoPhraseAway", "enum", options: state.phrases, title: "Use this phrase when away", required: false
    }
  }
}

def installed()
{
  initialize()
}

def updated()
{
  // When app is updated, clear out subscriptions and scheduled events and re-initialize
  unsubscribe()
  unschedule()
  initialize()
}

def initialize()
{
  log.debug "Initializing..."
  log.debug "Settings:"
  log.debug settings

  state.HOME = "home"
  state.AWAY = "away"

  subscribe(people, "presence", onPresence)
  subscribe(location, "sunrise", onSunrise)
  subscribe(location, "sunset", onSunset)

  // Custom dayparts are optional, make sure we have settings for them
  if(settings.customOneTime && settings.customOnePhrase && settings.customOnePhraseAway)
  {
    log.debug "Scheduling Custom One..."
    schedule(customOneTime, onCustomOne)
  }

  if(settings.customTwoTime && settings.customTwoPhrase && settings.customTwoPhraseAway)
  {
    log.debug "Scheduling Custom Two..."
    schedule(customTwoTime, onCustomTwo)
  }

  // Get initial values for presence and daypart, then run the proper phrase
  initializePresence()
  initializeDaypart()
  updatePhrase()
}

def initializePresence()
{
  log.debug "Determining initial presence state..."
  if(anyoneIsHome())
  {
    log.debug "Initial presence: home"
    state.presence = "home"
  }
  else
  {
    log.debug "Initial presence: away"
    state.presence = "away"
  }
}

def initializeDaypart()
{
  log.debug "Determining initial daypart state..."

  def tz = location.timeZone
  log.debug "Time zone: ${tz}"

  def now = new Date()
  log.debug now

  def sun = getSunriseAndSunset()
  log.debug sun

  // Put the dayparts and now into a map
  def dayparts = [:]
  dayparts["now"] = now
  dayparts["sunrise"] = sun.sunrise
  dayparts["sunset"] = sun.sunset

  // Custom dayparts are optional, make sure we have settings for them
  if(settings.customOneTime && settings.customOnePhrase && settings.customOnePhraseAway)
  {
    dayparts["customOne"] = timeToday(settings.customOneTime, tz)
  }
  if(settings.customTwoTime && settings.customTwoPhrase && settings.customTwoPhraseAway)
  {
    dayparts["customTwo"] = timeToday(settings.customTwoTime, tz)
  }

  log.debug dayparts

  // Sort the map in order of the dates
  dayparts = dayparts.sort { it.value }
  log.debug dayparts

  // Where is now in the sorted list of dayparts?
  def nowPosition = (dayparts.findIndexOf { it.key == "now" })
  log.debug nowPosition

  def currentDaypart

  // If now is the first item in the list,
  // the last daypart (from the previous day) is still active/current
  if(nowPosition == 0)
  {
    currentDaypart = dayparts.keySet().last()
  }
  else
  {
    // Otherwise, the active/current daypart is the one that started previous to now
    currentDaypart = dayparts.keySet()[nowPosition - 1]
  }

  log.debug currentDaypart
  state.daypart = currentDaypart
}

def onPresence(evt)
{
  log.debug "Presence event..."
  log.debug evt.name + " | " + evt.value

  def newPresence

  if(evt.value == "not present")
  {
    if(everyoneIsAway())
    {
      newPresence = "away"
    }
    else
    {
      newPresence = "home"
    }
  }
  else if(evt.value == "present")
  {
    newPresence = "home"
  }

  log.debug "New presence: " + newPresence
  log.debug "Current presence: " + state.presence

  // Only update if the presence has changed
  if(newPresence != state.presence)
  {
    state.presence = newPresence
    updatePhrase()
  }
}

// Event handlers for daypart events
def onSunset(evt){ onDaypartChange("sunrise") }
def onSunrise(evt){ onDaypartChange("sunset") }
def onCustomOne(evt){ onDaypartChange("customOne") }
def onCustomTwo(evt){ onDaypartChange("customTwo") }

private onDaypartChange(daypart)
{
  log.debug "New daypart: ${daypart}"
  log.debug "Current daypart: ${state.daypart}"

  if(daypart != state.daypart)
  {
    state.daypart = daypart
    log.debug "Daypart changed to: ${daypart}"
    updatePhrase()
  }
}

private updatePhrase()
{
  log.debug "Updating phrase..."
  def phrase = getPhrase()
  executePhrase(phrase)
}

private getPhrase()
{
  log.debug "The current presence state is: ${state.presence}"
  log.debug "The current daypart state is: ${state.daypart}"

  def phrase

  if(state.presence == "home"){ phrase = settings["${state.daypart}Phrase"] }
  else{ phrase = settings["${state.daypart}PhraseAway"] }

  return phrase
}

private executePhrase(phrase)
{
  log.debug "Executing phrase: ${phrase}"
  location.helloHome.execute(phrase)
}

private everyoneIsAway()
{
  return people.every{ it.currentPresence == "not present" }
}

private anyoneIsHome()
{
  return people.any{ it.currentPresence == "present" }
}
