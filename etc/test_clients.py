#!/usr/bin/env python

import subprocess
import time
import os

def run_client():
	rc = subprocess.call(['java', '-jar', 'sonar-test-@@VERSION@@.jar', '-c'])
	if rc:
		print 'client returned %d' % rc

for i in range(15):
	pid = os.fork()
	if pid == 0:
		run_client()
		break
	else:
		time.sleep(0.2)
