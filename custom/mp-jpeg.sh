rm -rf mp-jpeg
mkdir mp-jpeg
cat >mp-jpeg/plugins.ini <<EOF
com.fluendo.plugin.JPEGPlugin
com.fluendo.plugin.MultiPartPlugin
EOF

cd ..
cp --parents com/fluendo/player/*.class com/fluendo/utils/*.class com/fluendo/plugin/JPEGPlugin.class com/fluendo/plugin/MultiPart*.class custom/mp-jpeg/
cd custom

jar cvf mp-jpeg/cortado.jar mp-jpeg/*
