install:
	cp out/artifacts/Ce1_main_jar/Ce1.main.jar /usr/local/bin/Ce1.jar
	#cp slf4j-nop-2.0.0-alpha1.jar /usr/local/bin
	cp ce1.sh /usr/local/bin/ce1
	#cp freechains-host.sh         /usr/local/bin/freechains-host
	#cp freechains-sync.sh         /usr/local/bin/freechains-sync
	ls -l /usr/local/bin/[Cc]e*
	#freechains --version

test:
	echo "output std ()" > /tmp/tst.ce
	ce1 /tmp/tst.ce

gpp:
	gpp $(SRC).ce > $(SRC).cex
	ce1 $(SRC).cex
