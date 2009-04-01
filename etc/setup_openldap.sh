#!/bin/sh

yum install openldap-servers openldap-clients
cp ./slapd-test.conf /etc/openldap/slapd.conf
/sbin/service ldap start
sleep 5
ldapadd -x -D 'cn=admin,dc=sonar' -w password -f sonar-test.ldif
