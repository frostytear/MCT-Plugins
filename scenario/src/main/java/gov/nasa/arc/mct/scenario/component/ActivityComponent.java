/*******************************************************************************
 * Mission Control Technologies, Copyright (c) 2009-2012, United States Government
 * as represented by the Administrator of the National Aeronautics and Space 
 * Administration. All rights reserved.
 *
 * The MCT platform is licensed under the Apache License, Version 2.0 (the 
 * "License"); you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations under 
 * the License.
 *
 * MCT includes source code licensed under additional open source licenses. See 
 * the MCT Open Source Licenses file included with this distribution or the About 
 * MCT Licenses dialog available at runtime from the MCT Help menu for additional 
 * information. 
 *******************************************************************************/
package gov.nasa.arc.mct.scenario.component;

import gov.nasa.arc.mct.components.AbstractComponent;
import gov.nasa.arc.mct.components.JAXBModelStatePersistence;
import gov.nasa.arc.mct.components.ModelStatePersistence;
import gov.nasa.arc.mct.components.ObjectManager;
import gov.nasa.arc.mct.components.PropertyDescriptor;
import gov.nasa.arc.mct.components.PropertyDescriptor.VisualControlDescriptor;
import gov.nasa.arc.mct.components.PropertyEditor;
import gov.nasa.arc.mct.scenario.component.TimePropertyEditor.TimeProperty;
import gov.nasa.arc.mct.scenario.util.CostType;
import gov.nasa.arc.mct.services.component.ComponentTypeInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents an Activity user object.
 * 
 * Activities have start and end times, associated costs, and plain-text types. 
 * Activities may contain "sub-activities" (other activities); note that these 
 * sub-activities must always be contained entirely within their parent's 
 * time span.
 *
 */
public class ActivityComponent extends CostFunctionComponent implements DurationCapability {
	private ObjectManager objectManager = new ObjectManager.ExplicitObjectManager();
	private final AtomicReference<ActivityModel> model = new AtomicReference<ActivityModel>(new ActivityModel());
	
	/**
	 * Get the underlying data about this Activity (start time, end time, costs, type...)
	 * @return underlying activity data
	 */
	public ActivityModel getData() {
		return model.get();
	}

	@Override
	protected <T> List<T> handleGetCapabilities(Class<T> capability) {
		if (capability.isAssignableFrom(TagCapability.class)) {
			List<T> tagCapabilities = null;
			for (AbstractComponent child : getComponents()) {
				if (!(child instanceof ActivityComponent)) {
					List<T> childTags = child.getCapabilities(capability);
					if (childTags != null && !childTags.isEmpty()) {
						if (tagCapabilities == null) {
							tagCapabilities = new ArrayList<T>();
						}
						tagCapabilities.addAll(childTags);
					}
				}
			}
			if (tagCapabilities != null) {
				return tagCapabilities;
			}
		}

		return super.handleGetCapabilities(capability);
	}

	@Override
	protected <T> T handleGetCapability(Class<T> capability) {
		// Note: Don't report self as capability until initialized.
		if (capability.isAssignableFrom(getClass())) {
			return capability.cast(this);
		}
		if (capability.isAssignableFrom(ObjectManager.class)) {
			return capability.cast(objectManager);
		}
		if (capability.isAssignableFrom(ScenarioCSVExportCapability.class)) {
			return capability.cast(new ScenarioCSVExportCapability(this));
		}		
		if (capability.isAssignableFrom(ModelStatePersistence.class)) {
		    JAXBModelStatePersistence<ActivityModel> persistence = new JAXBModelStatePersistence<ActivityModel>() {

				@Override
				protected ActivityModel getStateToPersist() {
					return model.get();
				}

				@Override
				protected void setPersistentState(ActivityModel modelState) {
					model.set(modelState);
				}

				@Override
				protected Class<ActivityModel> getJAXBClass() {
					return ActivityModel.class;
				}
		        
			};
			
			return capability.cast(persistence);
		}
		return null;
	}
	
	@Override
	public List<CostFunctionCapability> getInternalCostFunctions() {
		List<CostFunctionCapability> internal = new ArrayList<CostFunctionCapability>();
		for(CostType type: CostType.values()) {
			double value = Double.valueOf(getModel().getValue(type.getName()));
			if (value != 0.0) {
				internal.add(new CostFunctionStub(type));
			}
		}
		internal.addAll(getCostWrappers());
		return internal;
	}
	
	@Override
	public List<CostCapability> getInternalCosts() {
		List<CostCapability> result = new ArrayList<CostCapability>();
		result.addAll(getCostWrappers());
		return result;
	}

	private List<CostWrapper> getCostWrappers() {
		Map<String, CostWrapper> costMap = new HashMap<String, CostWrapper>();
		List<CostWrapper> costs = new ArrayList<CostWrapper>();
		for (AbstractComponent child : getComponents()) {
			for (CostCapability c : child.getCapabilities(CostCapability.class)) {
				if (costMap.containsKey(c.getName())) {
					costMap.get(c.getName()).addCost(c);
				} else {
					CostWrapper wrapper = new CostWrapper(c);
					costMap.put(c.getName(), wrapper);
					costs.add(wrapper);
				}
			}
		}
		return costs;
	}
	
	/**
	 * Get the container for underlying Activity data
	 * @return the container for underlying Activity data (its model)
	 */
	public ActivityModel getModel() {
		return model.get();
	}

	@Override
	public List<PropertyDescriptor> getFieldDescriptors()  {

		// Provide an ordered list of fields to be included in the MCT Platform's InfoView.
		List<PropertyDescriptor> fields = new ArrayList<PropertyDescriptor>();


		// We specify a mutable text field.  The control display's values are maintained in the business model
		// via the PropertyEditor object.  When a new value is to be set, the editor also validates the prospective value.
		PropertyDescriptor startTime = new PropertyDescriptor("Start Time", 
				new TimePropertyEditor(this, TimeProperty.START),  VisualControlDescriptor.TextField);
		startTime.setFieldMutable(true);
		PropertyDescriptor endTime = new PropertyDescriptor("End Time", 
				new TimePropertyEditor(this, TimeProperty.END),  VisualControlDescriptor.TextField);
		endTime.setFieldMutable(true);
		PropertyDescriptor duration = new PropertyDescriptor("Duration",
				new TimePropertyEditor(this, TimeProperty.DURATION), VisualControlDescriptor.TextField);
		duration.setFieldMutable(true);
		PropertyDescriptor notes = new PropertyDescriptor("Notes", 
				new NotesPropertyEditor(this),  VisualControlDescriptor.TextArea);
		notes.setFieldMutable(true);
		PropertyDescriptor tags = new PropertyDescriptor("Associations", 
				new ActivityCustomPropertyEditor( 
						Arrays.asList(
								ScenarioPluginProvider.tagComponentType, 
								ScenarioPluginProvider.activityTypeComponentType)), 
				VisualControlDescriptor.Custom);
		tags.setFieldMutable(true);

		fields.add(startTime);
		fields.add(endTime);
		fields.add(duration);
		fields.addAll(super.getFieldDescriptors()); // Costs
		fields.add(notes);
		fields.add(tags);

		return fields;
	}
	
	/**
	 * Get the named type associated with this activity.
	 * @return
	 */
	public String getType() {
		return getData().getValue("type");
	}
	
	@Override
	public long getStart() {
		return Long.valueOf(getData().getValue("startTime"));
	}

	@Override
	public long getEnd() {
		return Long.valueOf(getData().getValue("endTime"));
	}	
	
	public void setType(String type) {
		getData().setValue("type", type);
	}

	@Override
	public void setStart(long start) {
		String startTime = String.valueOf(new Date(start > 0 ? start : 0).getTime());
		getData().setValue("startTime", startTime);
	}

	@Override
	public void setEnd(long end) {
		String endTime = String.valueOf(new Date(end > getStart() ? end : getStart()));
		getData().setValue("endTime", endTime);
	}

	
	/**
	 * Stub implementation of cost functions for activity components.
	 * In the future, this should be generalizable to include more than 
	 * just Comms and Power
	 * 
	 * @author vwoeltje
	 *
	 */
	private class CostFunctionStub implements CostFunctionCapability, CostCapability {
		private CostType type;
		
		private CostFunctionStub(CostType type) {
			this.type = type;
		}

		@Override
		public String getName() {
			return type.getName();
		}

		@Override
		public double getValue() {
			return Double.parseDouble(getData().getValue(getName()));
		}
		
		@Override
		public double getValue(long time) {
			if (time < getStart() || time >= getEnd()) {
				return 0;
			} else {
				return getValue();
			}
		}

		@Override
		public void setValue(double value) {
			getData().setValue(getName(), String.valueOf(value));			
		}
		
		public boolean isMutable() {
			return true;
		}
		
		@Override
		public Collection<Long> getChangeTimes() {
			return Arrays.asList(getStart(), getEnd());
		}

		@Override
		public String getInstantaniousUnits() {
			return type.getInstantaniousUnits();
		}

		@Override
		public String getAccumulativeUnits() {
			return type.getAccumulativeUnits();
		}

		@Override
		public CostType getCostType() {
			return type;
		}

	}
	
	private class CostWrapper implements CostFunctionCapability, CostCapability {
		private CostCapability baseCost;
		private Collection<CostCapability> costs = new HashSet<CostCapability>();

		public CostWrapper(CostCapability cost) {
			super();
			this.baseCost = cost;
			this.costs.add(cost);			
		}
		
		public void addCost(CostCapability cost) {
			costs.add(cost);
		}

		@Override
		public String getName() {
			return baseCost.getName();
		}

		@Override
		public double getValue(long time) {
			// Report zero outside of activity duration
			return time < getStart() || time >= getEnd() ?
					0.0 : getSum();			
		}
		
		private double getSum() {
			CostType type = getCostType();
			
			CostCapability[] arrayType =  new CostCapability[] {};
			CostCapability[] costArray;
			costArray = costs.toArray(arrayType);
		  
		    double sum = costArray[0].getValue();
		    int size = costs.size();
		    if (size > 1) {
		    	for (int i = 1; i < size; i++) {
			    	sum = type.add(sum, costArray[i].getValue()); 
			    }
		    }
		    
			/** if (costs.size() > 1) {
				
				for (CostCapability cost : costs) {
					sum = type.add(sum, cost.getValue()); 
				} 
				sum = type.add(sum, offset * -1);
			} */			
			return sum;
		}

		@Override
		public Collection<Long> getChangeTimes() {
			List<Long> result = new ArrayList<Long>();
			result.add(getStart());
			result.add(getEnd());
			return result;
		}

		@Override
		public double getValue() {
			return getSum();
		}

		@Override
		public void setValue(double value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isMutable() {
			return false;
		}

		@Override
		public String getInstantaniousUnits() {
			return baseCost.getInstantaniousUnits();
		}

		@Override
		public String getAccumulativeUnits() {
			return baseCost.getAccumulativeUnits();
		}

		@Override
		public CostType getCostType() {
			return baseCost.getCostType();
		}
		
		
	}
	
	private class ActivityCustomPropertyEditor implements PropertyEditor<ActivityCustomProperty> {
		private TagPropertyEditor tagPropertyEditor;
		
		public ActivityCustomPropertyEditor(Collection<ComponentTypeInfo> capabilities) {
			tagPropertyEditor = 
					new TagPropertyEditor(ActivityComponent.this, capabilities);
		}

		@Override
		public String getAsText() {
			return "";
		}

		@Override
		public void setAsText(String text) throws IllegalArgumentException {
			// Unused
		}

		@Override
		public Object getValue() {
			return new ActivityCustomProperty(tagPropertyEditor.getValue(), 
					getModel().getValue("url"),
					getModel().getValue("procedureUrl"));
		}

		@Override
		public void setValue(Object value) throws IllegalArgumentException {
			if (value instanceof ActivityCustomProperty) {
				ActivityCustomProperty property = (ActivityCustomProperty) value;
				tagPropertyEditor.setValue(property.getTagStyleChildren());
				getModel().setValue("url", property.getUrl());
				getModel().setValue("procedureUrl", property.getProcedureUrl());
			} else {
				throw new IllegalArgumentException(
						ActivityCustomProperty.class.getName() + 
						" expected, but " +
				        value.getClass().getName() + 
				        " passed instead.");
			}
		}

		@Override
		public List<ActivityCustomProperty> getTags() {
			return null;
		}
	}

	public static class ActivityCustomProperty {
		private Map<ComponentTypeInfo, List<AbstractComponent>> tagStyleChildren;
		private String url;
		private String procedureUrl;
		public ActivityCustomProperty(
				Map<ComponentTypeInfo, List<AbstractComponent>> tagStyleChildren,
				String url,
				String procedureUrl) {
			super();
			this.tagStyleChildren = tagStyleChildren;
			this.url = url;
			this.procedureUrl = procedureUrl;
		}
		public Map<ComponentTypeInfo, List<AbstractComponent>> getTagStyleChildren() {
			return tagStyleChildren;
		}
		public String getUrl() {
			return url;
		}
		public String getProcedureUrl() {
			return procedureUrl;
		}
	}
}