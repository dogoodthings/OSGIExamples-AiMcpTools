package org.dogoodthings.ectr.companion.tools;

import java.util.ArrayList;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.dscsag.plm.spi.interfaces.ECTRService;
import com.dscsag.plm.spi.interfaces.ai.tools.Tool;
import com.dscsag.plm.spi.interfaces.ai.tools.ToolParameter;
import com.dscsag.plm.spi.interfaces.ai.tools.ToolsComponent;
import com.dscsag.plm.spi.interfaces.rfc.RfcResult;
import com.dscsag.plm.spi.interfaces.rfc.RfcStructure;
import com.dscsag.plm.spi.interfaces.services.NumberConverterFactory;
import com.dscsag.plm.spi.interfaces.services.NumberConverterTypes;
import com.dscsag.plm.spi.rfc.builder.RfcCallBuilder;

@Component (service = ToolsComponent.class)
public class MaterialNetWeightMcpTool implements ToolsComponent
{
  @Reference ECTRService ectr;
  @Reference NumberConverterFactory numberConverterFactory;

  private static RfcStructure exportStructure(RfcResult result, String name)
  {
    var parameter = result.getExportParameter(name);
    return parameter != null && parameter.isStructure() ? parameter.getStructure() : null;
  }

  @Tool (value = "Reads NET_WEIGHT and UNIT_OF_WT for SAP materials using BAPI_MATERIAL_GET_DETAIL. Supply external or internal SAP material numbers. If SAP reports M3305, the external number is converted to internal format and retried. Returns successful materials and separate per-key errors. Does not modify SAP data.", name = "material_net_weights")
  public MatNetWeightResult call(@ToolParameter("materials to read weights for") MatNetWeightRequest request)
  {
    List<String> maraKeys = request.maraKeys();
    if (maraKeys.isEmpty())
      throw new IllegalArgumentException("No material keys supplied");

    List<String> materialKeys = new ArrayList<>();
    for (Object key : maraKeys)
    {
      if (!(key instanceof String materialKey) || materialKey.isBlank())
        throw new IllegalArgumentException("Invalid arguments: every MARA key must be a non-blank string.");
      materialKeys.add(materialKey);
    }

    List<MatNetWeight> materials = new ArrayList<>();
    List<MatError> errors = new ArrayList<>();
    ectr.getPlmLogger().verbose("MaterialNetWeightMcpTool reading " + materialKeys.size() + " materials");
    for (String key : materialKeys)
    {
      RfcResult result = readMaterial(key);
      if (hasMaterialNotFoundError(result))
      {
        try
        {
          String internalKey = numberConverterFactory.getConverter(NumberConverterTypes.MATERIAL).externalToInternal(key);
          if (!internalKey.equals(key))
            result = readMaterial(internalKey);
        }
        catch (IllegalArgumentException e)
        {
          errors.add(new MatError(key, "Could not convert external material number to internal format: " + e.getMessage()));
          continue;
        }
      }

      if (!result.isSuccessful())
      {
        String message = result.getErrorMessage();
        errors.add(new MatError(key,
            message == null || message.isBlank() ? "BAPI_MATERIAL_GET_DETAIL failed without an error message." : message));
        continue;
      }

      // RFC execution can succeed even when the BAPI reports an application error.
      RfcStructure returnData = exportStructure(result, "RETURN");
      if (returnData != null)
      {
        String type = returnData.getFieldValue("TYPE");
        if (type != null && List.of("E", "A", "X").contains(type.trim()))
        {
          String message = returnData.getFieldValue("MESSAGE");
          errors.add(new MatError(key,
              message == null || message.isBlank() ? "BAPI_MATERIAL_GET_DETAIL returned error type " + type : message));
          continue;
        }
      }

      RfcStructure data = exportStructure(result, "MATERIAL_GENERAL_DATA");
      String weight = data == null ? null : data.getFieldValue("NET_WEIGHT");
      String unit = data == null ? null : data.getFieldValue("UNIT_OF_WT");
      if (weight == null || weight.isBlank() || unit == null || unit.isBlank())
      {
        errors.add(new MatError(key, "SAP did not return NET_WEIGHT and UNIT_OF_WT."));
        continue;
      }
      materials.add(new MatNetWeight(key, weight, unit));
    }

    return new MatNetWeightResult(List.copyOf(materials), List.copyOf(errors));
  }

  private RfcResult readMaterial(String materialNumber)
  {
    RfcCallBuilder builder = new RfcCallBuilder("BAPI_MATERIAL_GET_DETAIL");
    builder.setInputParameter("MATERIAL_LONG", materialNumber);
    return ectr.getRfcExecutor().execute(builder.toRfcCall());
  }

  private static boolean hasMaterialNotFoundError(RfcResult result)
  {
    RfcStructure returnData = exportStructure(result, "RETURN");
    if (returnData == null)
      return false;

    String type = returnData.getFieldValue("TYPE");
    String code = returnData.getFieldValue("CODE");
    return type != null && "E".equalsIgnoreCase(type.trim())
        && code != null && "M3305".equalsIgnoreCase(code.trim());
  }

  public record MatNetWeight(String matNumber, String netWeight, String unit)
  {
  }

  public record MatError(String matNumber, String error)
  {
  }

  public record MatNetWeightResult(@ToolParameter(" net weight of materials ") List<MatNetWeight> materials, @ToolParameter("errors encountered while retrieving net weight") List<MatError> errors)
  {}

  public record MatNetWeightRequest(@ToolParameter("list of material numbers in internal format") List<String> maraKeys)
  {}

}
