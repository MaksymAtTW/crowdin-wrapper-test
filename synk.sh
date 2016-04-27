#export CROWDINW_HOME=.
export CROWDINW_PROJECT_API_KEY=21efd941b38122c3bc7bdfe2535421e8
./crowdinw.groovy -t ./crowdin/crowdin.yaml.tpl upload source 
# ./crowdinw.groovy -t ./crowdin/crowdin.yaml.tpl upload translations 
./crowdinw.groovy -t ./crowdin/crowdin.yaml.tpl download 
