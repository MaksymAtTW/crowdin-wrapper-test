---

api_key: <%= apiKey %>
project_identifier: cd-test
base_path: <%= basePath %>

#Add new properties files for translation below 
#Naming convention for translations - 'filename'+'_'+'langCode'+'.properties'
#eg. translate.messages_de.properties
files:
  -
  #TW WebApp Global
    source: /msg/recipient.properties
    translation: /msg/%file_name%_%two_letters_code%.%file_extension%
    
---
