%define __jar_repack %{nil}
%define debug_package %{nil}
%define __strip /bin/true
%define __os_install_post   /bin/true
%define __check_files /bin/true
Summary: notificationagent
Name: notificationagent
Version: 0.1.0
Release: 5
Epoch: 0
BuildArchitectures: noarch
Group: Applications
BuildRoot: %{_tmppath}/%{name}-%{version}-buildroot
License: BSD
Provides: notificationagent
Requires: iplant-service-config >= 0.1.0-4
Source0: %{name}-%{version}.tar.gz

%description
iPlant NotificationAgent

%pre
getent group iplant > /dev/null || groupadd -r iplant
getent passwd iplant > /dev/null || useradd -r -g iplant -d /home/iplant -s /bin/bash -c "User for the iPlant services." iplant
exit 0

%prep
%setup -q
mkdir -p $RPM_BUILD_ROOT/etc/init.d/

%build
unset JAVA_OPTS
lein deps
lein uberjar

%install
install -d $RPM_BUILD_ROOT/usr/local/lib/notificationagent/
install -d $RPM_BUILD_ROOT/var/run/notificationagent/
install -d $RPM_BUILD_ROOT/var/lock/subsys/notificationagent/
install -d $RPM_BUILD_ROOT/var/log/notificationagent/
install -d $RPM_BUILD_ROOT/etc/notificationagent/

install notificationagent $RPM_BUILD_ROOT/etc/init.d/
install notificationagent-1.0.0-SNAPSHOT-standalone.jar $RPM_BUILD_ROOT/usr/local/lib/notificationagent/
install conf/log4j.properties $RPM_BUILD_ROOT/etc/notificationagent/

%post
/sbin/chkconfig --add notificationagent

%preun
if [ $1 -eq 0 ] ; then
	/sbin/service notificationagent stop >/dev/null 2>&1
	/sbin/chkconfig --del notificationagent
fi

%postun
if [ "$1" -ge "1" ] ; then
	/sbin/service notificationagent condrestart >/dev/null 2>&1 || :
fi

%clean
lein clean
rm -r lib/*
rm -r $RPM_BUILD_ROOT

%files
%attr(-,iplant,iplant) /usr/local/lib/notificationagent/
%attr(-,iplant,iplant) /var/run/notificationagent/
%attr(-,iplant,iplant) /var/lock/subsys/notificationagent/
%attr(-,iplant,iplant) /var/log/notificationagent/
%attr(-,iplant,iplant) /etc/notificationagent/

%config %attr(0644,iplant,iplant) /etc/notificationagent/log4j.properties

%attr(0755,root,root) /etc/init.d/notificationagent
%attr(0644,iplant,iplant) /usr/local/lib/notificationagent/notificationagent-1.0.0-SNAPSHOT-standalone.jar
