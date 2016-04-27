---

api_key: <%= apiKey %>
project_identifier: cd-test
base_path: <%= basePath %>

#Add new properties files for translation below 
#Naming convention for translations - 'filename'+'_'+'langCode'+'.properties'
#eg. translate.messages_de.properties
files:

  -
    source: /msg/angular.json
    translation: /msg/angular_%locale%.%file_extension%
    languages_mapping:
          locale:
# crowdin_language_code: local_name
            'en-US': 'en_US'
            'es-ES': 'es'
            'zh-CN': 'zh'
            'pt-BR': 'pt'
            'de': 'de'
            'el': 'el'
            'et': 'et'
            'fr': 'fr'
            'hu': 'hu'
            'it': 'it'
            'ja': 'ja'
            'ko': 'ko'
            'pl': 'pl'
            'ru': 'ru'
            'en-GB': 'en'
            'es-MX': 'es_MX'
  -
    source: /msg/recipient.properties
    translation: /msg/%file_name%_%locale%.%file_extension%
    languages_mapping:
          locale:
# crowdin_language_code: local_name
            'en-US': 'en_US'
            'es-ES': 'es'
            'zh-CN': 'zh'
            'pt-BR': 'pt'
            'de': 'de'
            'el': 'el'
            'et': 'et'
            'fr': 'fr'
            'hu': 'hu'
            'it': 'it'
            'ja': 'ja'
            'ko': 'ko'
            'pl': 'pl'
            'ru': 'ru'
            'en-GB': 'en'
            'es-MX': 'es_MX'

            
  -
    source: /msg/subfolder/file.properties
#    translation: /msg/subfolder/%file_name%_%two_letters_code%.%file_extension%
    translation: /msg/subfolder/%file_name%_%locale%.%file_extension%
    languages_mapping:
          locale:
            'en-US': 'en_US'
            'es-ES': 'es'
            'zh-CN': 'zh'
            'pt-BR': 'pt'
            'de': 'de'
            'el': 'el'
            'et': 'et'
            'fr': 'fr'
            'hu': 'hu'
            'it': 'it'
            'ja': 'ja'
            'ko': 'ko'
            'pl': 'pl'
            'ru': 'ru'
            'en-GB': 'en'   
            'es-MX': 'es_MX'         
   
---
