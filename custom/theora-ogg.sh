rm -rf theora-ogg
mkdir theora-ogg
cat >theora-ogg/plugins.ini <<EOF
com.fluendo.plugin.TheoraPlugin
com.fluendo.plugin.OggPlugin
EOF

cd ..
cp --parents com/fluendo/player/*.class com/fluendo/utils/*.class com/fluendo/jheora/*.class  com/fluendo/plugin/TheoraPlugin.class com/fluendo/plugin/Ogg*.class com/jcraft/jogg/*.class custom/theora-ogg/
cd custom/theora-ogg

jar cvf cortado.jar com/* plugins.ini
cd ..
