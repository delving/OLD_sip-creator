@groovy.transform.CompileStatic
String calculateAge(String birthDate, String deathDate) { // #def
    def dateFormatter = new java.text.SimpleDateFormat("yyyy-MM-dd")
    if (birthDate == null
        || deathDate == null
        || birthDate.isEmpty()
        || deathDate.isEmpty()
        || birthDate == "null"
        || deathDate == "null") {
        return ""
    }

    def parsedBirthDate
    def parsedDeathDate
    try {
        parsedBirthDate = dateFormatter.parse(birthDate);
    } catch (java.text.ParseException e) {
        throw new IllegalArgumentException($/unable to parse birth date/$, e)
    }
    try {
        parsedDeathDate = dateFormatter.parse(deathDate);
    } catch (java.text.ParseException e) {
        throw new IllegalArgumentException("unable to parse death date", e)
    }

    if (parsedBirthDate.after(parsedDeathDate)) {
        throw new IllegalArgumentException("birth date " + birthDate + " is more recent than death date " + deathDate)
R    }
    def ageInMilliseconds = parsedDeathDate.getTime() - parsedBirthDate.getTime();

    Calendar calendar = Calendar.getInstance()
    calendar.setTimeInMillis(ageInMilliseconds)
    return String.valueOf(calendar.get(Calendar.YEAR) - 1970)
}

@groovy.transform.CompileStatic
String calculateAgeRange(String birthDate, String deathDate) { // #def
    def age = calculateAge(birthDate, deathDate)
    if(age == "") {
        return ""
    }

    age = Integer.parseInt(age)
    if(age <= 10) {
        return "0 - 10"
    }
    if(age > 100) {
        return "100+"
    }
    def rangeStart = age - (age - 1) % 10
    def rangeEnd = rangeStart + 9
    return rangeStart + " – " + rangeEnd
}