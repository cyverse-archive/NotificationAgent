Summary: iPlant Notification Agent
Name: iplant-notificationagent
Version: 0.2.0
Release: 3
Epoch: 0
Group: Applications
BuildRoot: %{_tmppath}/%{name}-%{version}-buildroot
License: foo
Provides: iplant-notificationagent
Requires: node >= v0.2.2
Requires: iplant-node-launch >= 0.0.1-5
Requires: iplant-nodejs-libs
Source0: %{name}-%{version}.tar.gz

%description
iPlant Notification Agent

%pre
getent group iplant > /dev/null || groupadd -r iplant
getent passwd iplant > /dev/null || useradd -r -g iplant -d /home/iplant -s /bin/bash -c "User for the iPlant services." iplant
exit 0

%prep
%setup -q
mkdir -p $RPM_BUILD_ROOT
mkdir -p $RPM_BUILD_ROOT/usr/local/lib/node/iplant-notificationagent
mkdir -p $RPM_BUILD_ROOT/usr/local/bin
mkdir -p $RPM_BUILD_ROOT/var/log/iplant-notificationagent/
mkdir -p $RPM_BUILD_ROOT/etc/init.d/
mkdir -p $RPM_BUILD_ROOT/etc/logrotate.d/

%build

%install
cp src/* $RPM_BUILD_ROOT/usr/local/lib/node/iplant-notificationagent/
cp conf/template.conf $RPM_BUILD_ROOT/etc/iplant-notificationagent.conf
cp conf/logrotate.conf $RPM_BUILD_ROOT/etc/logrotate.d/iplant-notificationagent
install -m755 src/iplant-notificationagent $RPM_BUILD_ROOT/etc/init.d/

%clean
rm -rf $RPM_BUILD_ROOT

%files
%defattr(0764,iplant,iplant)
%attr(0775,iplant,iplant) /usr/local/lib/node/iplant-notificationagent
%config %attr(0644,root,root) /etc/iplant-notificationagent.conf
%config %attr(0644,root,root) /etc/logrotate.d/iplant-notificationagent
%attr(0755,root,root) /etc/init.d/iplant-notificationagent
