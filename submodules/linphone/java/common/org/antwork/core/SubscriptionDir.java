package org.antwork.core;

public enum SubscriptionDir {
	Incoming(0),
	Outgoing(1),
	Invalid(2);
	protected final int mValue;
	private SubscriptionDir(int value){
		mValue=value;
	}
	static protected SubscriptionDir fromInt(int value){
		switch(value){
		case 0: return Incoming;
		case 1: return Outgoing;
		}
		return Invalid;
	}
}
