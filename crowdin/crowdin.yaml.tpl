---

api_key: <%= apiKey %>
project_identifier: cd-test
base_path: <%= basePath %>

#Add new properties files for translation below 
#Naming convention for translations - 'filename'+'_'+'langCode'+'.properties'
#eg. translate.messages_de.properties
files:
  -
    source: /msg/recipient.properties
    translation: /msg/%file_name%_%two_letters_code%.%file_extension%
    languages_mapping:
          two_letters_code:
            'en-US': 'en_US'
            'en-en': 'en'

    update_option: 'update_as_unapproved'

  -
    source: /msg/subfolder/file.properties
    translation: /msg/subfolder/%file_name%_%two_letters_code%.%file_extension%
    languages_mapping:
          two_letters_code:
            'en-US': 'en_US'    
            'en-en': 'en'
    update_option: 'update_without_changes'
    
    
---
